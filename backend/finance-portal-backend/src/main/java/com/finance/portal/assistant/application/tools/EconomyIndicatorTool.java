package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.market.application.economy.EconomyService;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Türkiye güncel makroekonomik göstergelerini TCMB EVDS'ten döndürür (politika faizi, enflasyon,
 * işsizlik, büyüme, cari denge, kur, bütçe, kapasite, güven, BIST100, gram altın, ABD enflasyonu).
 *
 * <p>Veri {@link EconomyService#getSummary()} ile alınır (katalog + son değer + dönemsel/yıllık değişim,
 * 6 saat cache). LLM ham sayıları değil, getirilen gerçek değeri YORUMLAR. Böylece "faiz/enflasyon ne
 * durumda" gibi sorulara "TCMB sitesine bak" demek yerine gerçek veriyle yanıt verilir.
 */
@Component
public class EconomyIndicatorTool implements AssistantTool {

    /** Filtre verilmediğinde dönen öne çıkan göstergeler (katalog anahtarları). */
    private static final List<String> HEADLINE = List.of(
            "politikaFaizi", "tufe", "ufe", "mevduatFaizi", "issizlik", "gsyihBuyume", "usdTry");

    /** Geniş konu anahtar kelimesi → kategori (sırayla denenir; ilk eşleşen kazanır). */
    private static final List<String[]> CATEGORY_SYNONYMS = List.of(
            new String[]{"politika faiz", "RATES"},
            new String[]{"faiz", "RATES"},
            new String[]{"repo", "RATES"},
            new String[]{"enflasyon", "INFLATION"},
            new String[]{"buyume", "GROWTH"},
            new String[]{"gsyih", "GROWTH"},
            new String[]{"milli gelir", "GROWTH"},
            new String[]{"issizlik", "LABOR"},
            new String[]{"istihdam", "LABOR"},
            new String[]{"isgucu", "LABOR"},
            new String[]{"kur", "FX"},
            new String[]{"doviz", "FX"},
            new String[]{"cari", "EXTERNAL"},
            new String[]{"rezerv", "EXTERNAL"},
            new String[]{"butce", "FISCAL"},
            new String[]{"kapasite", "ACTIVITY"},
            new String[]{"guven", "ACTIVITY"},
            new String[]{"borsa", "MARKETS"});

    private final EconomyService economyService;

    public EconomyIndicatorTool(EconomyService economyService) {
        this.economyService = economyService;
    }

    @Override
    public String name() {
        return "get_economy_indicator";
    }

    @Override
    public String description() {
        return "Türkiye güncel makroekonomik göstergelerini TCMB EVDS'ten döndürür: politika faizi, "
                + "enflasyon (TÜFE/Yİ-ÜFE/çekirdek), mevduat & kredi faizi, büyüme (GSYİH), işsizlik, "
                + "cari denge, TCMB rezervleri, dolar/euro kuru, bütçe dengesi, kapasite kullanımı, "
                + "tüketici güveni, BIST100, gram altın, ABD enflasyonu. "
                + "indicator (opsiyonel): konu/anahtar kelime (örn 'faiz', 'politika faizi', 'enflasyon', "
                + "'tüfe', 'işsizlik', 'dolar', 'büyüme', 'cari', 'bütçe'); boş bırakılırsa öne çıkan "
                + "göstergelerin özetini verir.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "indicator", Map.of(
                                "type", "string",
                                "description", "Konu/anahtar kelime (opsiyonel): faiz, politika faizi, "
                                        + "enflasyon, tüfe, üfe, çekirdek, mevduat, kredi, büyüme, gsyih, "
                                        + "işsizlik, dolar, euro, kur, cari, rezerv, bütçe, kapasite, güven, "
                                        + "bist, altın. Boşsa öne çıkanların özeti döner.")),
                "required", List.of());
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        String q = norm(text(args, "indicator"));

        List<EconomyIndicator> all;
        try {
            all = economyService.getSummary();
        } catch (Exception e) {
            return "Ekonomik göstergeler şu an alınamadı.";
        }
        if (all == null || all.isEmpty()) {
            return "Ekonomik gösterge verisi bulunamadı.";
        }

        List<EconomyIndicator> picked = select(all, q);
        boolean fallback = false;
        if (picked.isEmpty()) {
            picked = byKeys(all, HEADLINE);
            fallback = !q.isBlank();
        }

        StringBuilder sb = new StringBuilder();
        if (fallback) {
            sb.append("'").append(text(args, "indicator").trim())
                    .append("' için doğrudan eşleşme yok; öne çıkan göstergeler:\n");
        } else {
            sb.append("Türkiye ekonomik göstergeleri (Kaynak: TCMB EVDS):\n");
        }
        for (EconomyIndicator ind : picked) {
            sb.append("• ").append(format(ind)).append("\n");
        }
        sb.append("(Değerler en son yayınlanan resmi dönemlere aittir; yatırım tavsiyesi değildir.)");
        return sb.toString().trim();
    }

    /** Soruya göre gösterge seçer: boş→öne çıkanlar, geniş konu→kategori, aksi→ad/anahtar içinde arama. */
    private List<EconomyIndicator> select(List<EconomyIndicator> all, String q) {
        if (q.isBlank()) {
            return byKeys(all, HEADLINE);
        }
        for (String[] syn : CATEGORY_SYNONYMS) {
            if (q.contains(syn[0])) {
                return byCategory(all, syn[1]);
            }
        }
        List<EconomyIndicator> out = new ArrayList<>();
        for (EconomyIndicator i : all) {
            String key = norm(i.getKey());
            String label = norm(i.getLabel());
            if (label.contains(q) || key.contains(q)) {
                out.add(i);
            }
        }
        return out;
    }

    private static List<EconomyIndicator> byCategory(List<EconomyIndicator> all, String category) {
        List<EconomyIndicator> out = new ArrayList<>();
        for (EconomyIndicator i : all) {
            if (category.equals(i.getCategory())) {
                out.add(i);
            }
        }
        return out;
    }

    private static List<EconomyIndicator> byKeys(List<EconomyIndicator> all, List<String> keys) {
        List<EconomyIndicator> out = new ArrayList<>();
        for (String k : keys) {
            for (EconomyIndicator i : all) {
                if (k.equals(i.getKey())) {
                    out.add(i);
                    break;
                }
            }
        }
        return out;
    }

    private static String format(EconomyIndicator ind) {
        if (!ind.isAvailable() || ind.getValue() == null) {
            return ind.getLabel() + ": (güncel veri yok)";
        }
        StringBuilder b = new StringBuilder();
        b.append(ind.getLabel()).append(": ").append(num(ind.getValue()));
        String unit = ind.getUnit();
        if (unit != null && !unit.isBlank()) {
            b.append("%".equals(unit) ? "%" : " " + unit);
        }
        if (ind.getPeriod() != null && !ind.getPeriod().isBlank()) {
            b.append(" (dönem: ").append(ind.getPeriod()).append(")");
        }
        if (ind.isPreferAbsolute() && ind.getAbsoluteChange() != null) {
            b.append(" | önceki döneme göre ").append(signed(ind.getAbsoluteChange()));
        } else if (ind.getChangePercent() != null) {
            b.append(" | önceki döneme göre ").append(signed(ind.getChangePercent())).append("%");
        }
        if (ind.getYoyChangePercent() != null) {
            b.append(" | yıllık ").append(signed(ind.getYoyChangePercent())).append("%");
        }
        return b.toString();
    }

    private static String num(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    private static String signed(BigDecimal v) {
        if (v == null) {
            return "-";
        }
        BigDecimal s = v.stripTrailingZeros();
        return (s.signum() >= 0 ? "+" : "") + s.toPlainString();
    }

    /** Türkçe karakterleri sadeleştirip küçük harfe çevirir (eşleştirme için). */
    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT)
                .replace('ı', 'i').replace('İ', 'i')
                .replace('ş', 's').replace('Ş', 's')
                .replace('ğ', 'g').replace('Ğ', 'g')
                .replace('ü', 'u').replace('Ü', 'u')
                .replace('ö', 'o').replace('Ö', 'o')
                .replace('ç', 'c').replace('Ç', 'c')
                .replace('â', 'a').replace('î', 'i').replace('û', 'u')
                .replace("̇", "")
                .trim();
    }

    private static String text(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        return node != null && !node.isNull() ? node.asText("").trim() : "";
    }
}
