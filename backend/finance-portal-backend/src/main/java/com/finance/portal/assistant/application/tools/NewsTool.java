package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsQueryResult;
import com.finance.portal.news.application.service.NewsAggregatorService;
import com.finance.portal.news.domain.NewsCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Güncel haber başlıklarını döndürür (çok kaynaklı agregatörden). LLM bunları özetler.
 */
@Component
public class NewsTool implements AssistantTool {

    private static final int LIMIT = 5;

    private final NewsAggregatorService aggregator;

    public NewsTool(NewsAggregatorService aggregator) {
        this.aggregator = aggregator;
    }

    @Override
    public String name() {
        return "get_news";
    }

    @Override
    public String description() {
        return "Güncel finans/ekonomi haber başlıklarını döndürür (özetlemek için). "
                + "Kullanıcı belirli bir alandan söz ederse UYGUN category'i geç: 'ekonomi' → ECONOMY, "
                + "'borsa/hisse' → STOCKS, 'döviz/dolar/euro' → FX, 'tahvil/faiz' → BONDS, "
                + "'kripto/bitcoin' → CRYPTO, 'altın/emtia' → COMMODITIES, 'dünya/global' → WORLD. "
                + "category (isteğe bağlı): ECONOMY, STOCKS, FX, BONDS, CRYPTO, COMMODITIES, WORLD. "
                + "query (isteğe bağlı): anahtar kelime (örn 'dolar', 'merkez bankası').";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "category", Map.of(
                                "type", "string",
                                "enum", List.of("ECONOMY", "STOCKS", "FX", "BONDS", "CRYPTO", "COMMODITIES", "WORLD"),
                                "description", "Haber kategorisi (opsiyonel)"),
                        "query", Map.of("type", "string", "description", "Anahtar kelime (opsiyonel)")),
                "required", List.of());
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        String categoryArg = text(args, "category");
        String query = text(args, "query");
        NewsCategory cat = NewsCategory.fromString(categoryArg);
        String categoryName = cat != null ? cat.name() : null;

        try {
            NewsQueryResult result = aggregator.query(
                    categoryName, null, query.isBlank() ? null : query, "TR",
                    null, null, 1, LIMIT, null);
            List<NewsArticle> items = result != null ? result.items() : List.of();
            if (items == null || items.isEmpty()) {
                return "Şu an gösterilecek haber bulunamadı.";
            }
            // Sonucun başına AÇIK bir talimat koy: zayıf modeller (Groq Llama fallback) aksi halde bu
            // başlıkları yok sayıp "ekonomi dünyasında gelişmeler var" gibi GENEL laf üretiyor. Talimat
            // veriyle aynı mesajda olunca (sistem promptundan uzakta değil) modelin uyma olasılığı çok artar.
            StringBuilder sb = new StringBuilder(
                    "TALİMAT: Aşağıdaki GERÇEK haber başlıklarını kullanıcıya madde madde (•) AYNEN aktar; "
                            + "her birini istersen 1 cümleyle kısaca özetle. Başlık UYDURMA, GENEL/boş laf etme.\n\n"
                            + "Güncel başlıklar:\n");
            int n = 0;
            for (NewsArticle a : items) {
                if (a.getTitle() == null || a.getTitle().isBlank()) {
                    continue;
                }
                sb.append("• ").append(a.getTitle().trim());
                if (a.getSource() != null && !a.getSource().isBlank()) {
                    sb.append(" (").append(a.getSource().trim()).append(")");
                }
                sb.append("\n");
                if (++n >= LIMIT) {
                    break;
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Haberler alınamadı.";
        }
    }

    private static String text(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        return node != null && !node.isNull() ? node.asText("").trim() : "";
    }
}
