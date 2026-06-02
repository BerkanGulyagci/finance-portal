package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioCurrencyConverter;
import com.finance.portal.portfolio.service.PortfolioService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kullanıcının MEVCUT portföyüne hipotetik bir ŞOK uygulayıp yeni değeri DETERMİNİSTİK hesaplar
 * ("petrol 60 dolara inerse / borsa %20 düşerse / dolar 50 olursa portföyüm ne olur").
 *
 * <p>İş bölümü: <b>AI senaryoyu TASARLAR</b> (doğal dili "şok vektörüne" çevirir; hedef fiyat verilmişse
 * önce {@code get_current_price} ile yüzdeye dönüştürür), <b>bu araç HESAPLAR</b> (şoku TL değerlerine
 * uygular), <b>AI sonucu YORUMLAR</b>. Böylece sayı uydurma olmaz; hesap koddadır.
 */
@Component
public class ScenarioSimulationTool implements AssistantTool {

    private static final int MAX_LINES = 10;

    private final PortfolioService portfolioService;
    private final PortfolioCurrencyConverter currencyConverter;

    public ScenarioSimulationTool(PortfolioService portfolioService, PortfolioCurrencyConverter currencyConverter) {
        this.portfolioService = portfolioService;
        this.currencyConverter = currencyConverter;
    }

    @Override
    public String name() {
        return "simulate_portfolio_scenario";
    }

    @Override
    public String description() {
        return "Kullanıcının MEVCUT portföyüne hipotetik bir ŞOK uygular ve yeni değeri hesaplar "
                + "(\"petrol 60 dolara inerse\", \"borsa %20 düşerse\", \"dolar 50 olursa portföyüm ne olur\"). "
                + "shocks: her biri {target, change_percent} olan dizi. target = varlık TİPİ "
                + "(STOCK/CRYPTO/FX/GOLD/COMMODITY/FUND/BOND/FUTURE/SILVER), belirli SEMBOL (ör. CL=F, THYAO.IS) "
                + "veya ALL (tüm portföy). change_percent = yüzde değişim (-14 = %14 düşüş, +22 = %22 artış). "
                + "Kullanıcı hedef FİYAT söylerse (ör. 'petrol 60$') önce get_current_price ile güncel fiyatı al, "
                + "yüzde değişime ÇEVİR, sonra bu aracı çağır. portfolio_name opsiyonel.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "shocks", Map.of(
                                "type", "array",
                                "description", "Uygulanacak şok listesi",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "target", Map.of("type", "string",
                                                        "description", "Varlık tipi, sembol veya ALL"),
                                                "change_percent", Map.of("type", "number",
                                                        "description", "Yüzde değişim (ör. -14)")),
                                        "required", List.of("target", "change_percent"))),
                        "portfolio_name", Map.of("type", "string", "description", "Opsiyonel portföy adı")),
                "required", List.of("shocks"));
    }

    private record Shock(String target, double pct) {}

    private record Mover(String label, BigDecimal oldTl, BigDecimal newTl, double pct) {}

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        if (ctx == null || !ctx.isAuthenticated()) {
            return "Kullanıcı giriş yapmamış. Senaryo simülasyonu için kullanıcının giriş yapması gerekir.";
        }
        List<Shock> shocks = parseShocks(args);
        if (shocks.isEmpty()) {
            return "Senaryo için en az bir şok gerekir (ör. target='COMMODITY', change_percent=-14).";
        }
        String wantedName = text(args, "portfolio_name");
        try {
            List<PortfolioResponse> all = portfolioService.getUserPortfolios(ctx.userId()).stream()
                    .filter(p -> p.getPortfolioType() != PortfolioType.WATCHLIST)
                    .collect(Collectors.toList());
            if (all.isEmpty()) {
                return "Kullanıcının (izleme listesi dışında) portföyü yok.";
            }
            List<PortfolioResponse> scope = all;
            String scopeLabel = all.size() == 1 ? "\"" + all.get(0).getName() + "\"" : (all.size() + " portföy birleşik");
            if (!wantedName.isBlank()) {
                List<PortfolioResponse> match = all.stream()
                        .filter(p -> p.getName() != null
                                && p.getName().toLowerCase(Locale.ROOT).contains(wantedName.toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
                if (!match.isEmpty()) {
                    scope = match;
                    scopeLabel = "\"" + match.get(0).getName() + "\"";
                }
            }

            BigDecimal oldTotal = BigDecimal.ZERO;
            BigDecimal newTotal = BigDecimal.ZERO;
            BigDecimal shockedValue = BigDecimal.ZERO;
            List<Mover> movers = new ArrayList<>();
            for (PortfolioResponse p : scope) {
                if (p.getHoldings() == null) {
                    continue;
                }
                for (PortfolioHoldingResponse h : p.getHoldings()) {
                    if (h.isClosed()) {
                        continue;
                    }
                    BigDecimal tl = currencyConverter.toTry(h.getMarketValue(), h.getCurrency());
                    if (tl == null || tl.signum() <= 0) {
                        continue;
                    }
                    oldTotal = oldTotal.add(tl);
                    double pct = shockFor(h, shocks);
                    BigDecimal nt = tl.multiply(BigDecimal.valueOf(1 + pct / 100.0));
                    newTotal = newTotal.add(nt);
                    if (pct != 0) {
                        shockedValue = shockedValue.add(tl);
                        movers.add(new Mover(label(h), tl, nt, pct));
                    }
                }
            }
            if (oldTotal.signum() <= 0) {
                return scopeLabel + " boş ya da değeri hesaplanamadı.";
            }

            BigDecimal delta = newTotal.subtract(oldTotal);
            BigDecimal deltaPct = delta.multiply(BigDecimal.valueOf(100)).divide(oldTotal, 2, RoundingMode.HALF_UP);
            BigDecimal coverage = shockedValue.multiply(BigDecimal.valueOf(100)).divide(oldTotal, 0, RoundingMode.HALF_UP);

            StringBuilder sb = new StringBuilder();
            sb.append("Senaryo simülasyonu — ").append(scopeLabel).append(":\n");
            sb.append("Uygulanan şoklar: ").append(shocks.stream()
                    .map(s -> trTarget(s.target()) + " %" + trimNum(s.pct())).collect(Collectors.joining(", "))).append("\n");
            sb.append("Mevcut değer: ").append(fmt(oldTotal)).append(" TL → Senaryo sonrası: ").append(fmt(newTotal))
                    .append(" TL (değişim ").append(fmt(delta)).append(" TL, %").append(fmt(deltaPct)).append(")\n");
            if (!movers.isEmpty()) {
                movers.sort(Comparator.comparing(Mover::oldTl, Comparator.reverseOrder()));
                sb.append("Etkilenen pozisyonlar (TL):\n");
                int n = 0;
                for (Mover m : movers) {
                    if (n++ >= MAX_LINES) {
                        sb.append("… (+").append(movers.size() - MAX_LINES).append(" pozisyon daha)\n");
                        break;
                    }
                    sb.append("• ").append(m.label()).append(": ").append(fmt(m.oldTl())).append(" → ")
                            .append(fmt(m.newTl())).append(" (%").append(trimNum(m.pct())).append(")\n");
                }
            }
            sb.append("Şoktan etkilenen portföy oranı: %").append(coverage).append(".\n");
            sb.append("(Hipotetik senaryo; gerçek piyasa tepkisi farklı olabilir. Yatırım tavsiyesi değildir.)");
            return sb.toString();
        } catch (Exception e) {
            return "Senaryo hesaplanamadı.";
        }
    }

    /** Sembol > tip > ALL önceliğiyle holding'e uygulanacak yüzdeyi seçer. */
    private static double shockFor(PortfolioHoldingResponse h, List<Shock> shocks) {
        String sym = h.getSymbol() != null ? h.getSymbol().trim() : "";
        String type = h.getAssetType() != null ? h.getAssetType().name() : "";
        Double bySym = null;
        Double byType = null;
        Double byAll = null;
        for (Shock s : shocks) {
            String t = s.target().trim();
            if (!sym.isEmpty() && t.equalsIgnoreCase(sym)) {
                bySym = s.pct();
            } else if (!type.isEmpty() && t.equalsIgnoreCase(type)) {
                byType = s.pct();
            } else if (t.equalsIgnoreCase("ALL")) {
                byAll = s.pct();
            }
        }
        if (bySym != null) {
            return bySym;
        }
        if (byType != null) {
            return byType;
        }
        return byAll != null ? byAll : 0;
    }

    private static List<Shock> parseShocks(JsonNode args) {
        List<Shock> out = new ArrayList<>();
        JsonNode arr = args != null ? args.get("shocks") : null;
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String target = n.hasNonNull("target") ? n.get("target").asText("").trim() : "";
                JsonNode pctNode = firstNonNull(n, "change_percent", "changePercent", "percent", "change");
                if (!target.isEmpty() && pctNode != null && !pctNode.isNull()) {
                    out.add(new Shock(target, pctNode.asDouble(0)));
                }
            }
        }
        return out;
    }

    private static JsonNode firstNonNull(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    private static String label(PortfolioHoldingResponse h) {
        if (h.getName() != null && !h.getName().isBlank()) {
            return h.getName();
        }
        return h.getSymbol() != null ? h.getSymbol() : "?";
    }

    private static String trTarget(String t) {
        return switch (t.toUpperCase(Locale.ROOT)) {
            case "STOCK" -> "Hisse";
            case "CRYPTO" -> "Kripto";
            case "FX" -> "Döviz";
            case "GOLD" -> "Altın";
            case "SILVER" -> "Gümüş";
            case "COMMODITY" -> "Emtia";
            case "FUND" -> "Fon";
            case "BOND" -> "Tahvil";
            case "FUTURE" -> "Vadeli";
            case "ALL" -> "Tüm portföy";
            default -> t;
        };
    }

    private static String trimNum(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String text(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        return node != null && !node.isNull() ? node.asText("").trim() : "";
    }
}
