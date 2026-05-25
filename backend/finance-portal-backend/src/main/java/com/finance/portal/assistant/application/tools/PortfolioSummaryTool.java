package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Giriş yapan kullanıcının portföyünü özetler: tüm portföyler birleşik VEYA belirli bir isimli portföy.
 * Veriler TL (tüm türler TRY-normalize). Yatırım tavsiyesi vermez — mevcut dağılımı/K-Z'yi betimler.
 */
@Component
public class PortfolioSummaryTool implements AssistantTool {

    private static final int MAX_LINES = 15;

    private final PortfolioService portfolioService;

    public PortfolioSummaryTool(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Override
    public String name() {
        return "get_portfolio_summary";
    }

    @Override
    public String description() {
        return "Giriş yapan kullanıcının portföyünü özetler: toplam değer (TL), toplam kâr/zarar, varlık türü "
                + "dağılımı (%). portfolio_name verilirse SADECE o isimli portföy; boşsa tümü birleşik. "
                + "detailed=true verilirse varlık BAZINDA döküm de eklenir (sadece kullanıcı detay/dökümü açıkça "
                + "isteyince true yap). 'portföyüm', 'kârım', 'dağılımım', 'X portföyümü analiz et' sorularında kullan.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "portfolio_name", Map.of(
                                "type", "string",
                                "description", "Analiz edilecek portföyün adı (opsiyonel; boşsa tümü birleşik)"),
                        "detailed", Map.of(
                                "type", "boolean",
                                "description", "Varlık bazında dökümü ekle (kullanıcı detay isteyince true)")),
                "required", List.of());
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        if (ctx == null || !ctx.isAuthenticated()) {
            return "Kullanıcı giriş yapmamış. Portföy analizi için kullanıcının giriş yapması gerekir.";
        }
        String wantedName = text(args, "portfolio_name");
        boolean detailed = boolArg(args, "detailed");
        try {
            List<PortfolioResponse> all = portfolioService.getUserPortfolios(ctx.userId()).stream()
                    .filter(p -> p.getPortfolioType() != PortfolioType.WATCHLIST)
                    .collect(Collectors.toList());
            if (all.isEmpty()) {
                return "Kullanıcının (izleme listesi dışında) portföyü yok.";
            }
            List<String> names = all.stream().map(PortfolioResponse::getName).collect(Collectors.toList());

            List<PortfolioResponse> scope;
            String scopeLabel;
            if (!wantedName.isBlank()) {
                scope = all.stream()
                        .filter(p -> p.getName() != null && p.getName().trim().equalsIgnoreCase(wantedName.trim()))
                        .collect(Collectors.toList());
                if (scope.isEmpty()) {
                    // İsim eşleşmedi → kısmi eşleşme dene
                    scope = all.stream()
                            .filter(p -> p.getName() != null
                                    && p.getName().toLowerCase(Locale.ROOT).contains(wantedName.toLowerCase(Locale.ROOT)))
                            .collect(Collectors.toList());
                }
                if (scope.isEmpty()) {
                    return "\"" + wantedName + "\" adlı portföy bulunamadı. Mevcut portföyler: "
                            + String.join(", ", names) + ". Hangisini analiz edeyim?";
                }
                scopeLabel = "\"" + scope.get(0).getName() + "\" portföyü";
            } else {
                scope = all;
                scopeLabel = all.size() == 1 ? "\"" + names.get(0) + "\" portföyü" : (all.size() + " portföy birleşik");
            }

            List<PortfolioHoldingResponse> holdings = new ArrayList<>();
            for (PortfolioResponse p : scope) {
                if (p.getHoldings() != null) {
                    holdings.addAll(p.getHoldings());
                }
            }
            if (holdings.isEmpty()) {
                return scopeLabel + " boş (varlık yok). Mevcut portföyler: " + String.join(", ", names) + ".";
            }

            String summary = summarize(scopeLabel, holdings, detailed);
            // Tek portföy analiz edildiyse ve birden fazla portföy varsa, diğerlerini de hatırlat.
            if (!wantedName.isBlank() && all.size() > 1) {
                final String selectedName = scope.get(0).getName();
                summary += "\n(Diğer portföyler: " + names.stream()
                        .filter(n -> n != null && !n.equalsIgnoreCase(selectedName))
                        .collect(Collectors.joining(", ")) + ")";
            }
            return summary;
        } catch (Exception e) {
            return "Portföy verisi alınamadı.";
        }
    }

    private String summarize(String scopeLabel, List<PortfolioHoldingResponse> holdings, boolean detailed) {
        BigDecimal totalMv = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPl = BigDecimal.ZERO;
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        for (PortfolioHoldingResponse h : holdings) {
            BigDecimal mv = nz(h.getMarketValue());
            totalMv = totalMv.add(mv);
            totalCost = totalCost.add(nz(h.getTotalCost()));
            totalPl = totalPl.add(nz(h.getProfitLoss()));
            String type = h.getAssetType() != null ? h.getAssetType().name() : "DİĞER";
            byType.merge(type, mv, BigDecimal::add);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(scopeLabel).append(" — Toplam değer: ").append(fmt(totalMv)).append(" TL. ");
        sb.append("Toplam maliyet: ").append(fmt(totalCost)).append(" TL. ");
        sb.append("Toplam K/Z: ").append(fmt(totalPl)).append(" TL");
        if (totalCost.signum() > 0) {
            sb.append(" (").append(fmt(totalPl.multiply(BigDecimal.valueOf(100))
                    .divide(totalCost, 2, RoundingMode.HALF_UP))).append("%)");
        }
        sb.append(".\n");

        sb.append("Varlık türü dağılımı: ");
        List<String> alloc = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : byType.entrySet()) {
            if (totalMv.signum() > 0) {
                BigDecimal pct = e.getValue().multiply(BigDecimal.valueOf(100))
                        .divide(totalMv, 1, RoundingMode.HALF_UP);
                alloc.add(typeTr(e.getKey()) + " %" + fmt(pct));
            }
        }
        sb.append(String.join(", ", alloc)).append(".");

        if (!detailed) {
            // Döküm OTOMATİK verilmez; LLM "detaylı dökümü ister misin?" diye sorar (sistem promptu).
            sb.append("\n[DÖKÜM_YOK: Varlık bazında döküm istenirse detailed=true ile tekrar çağır.]");
            return sb.toString().trim();
        }

        sb.append("\n");
        holdings.sort((a, b) -> nz(b.getMarketValue()).compareTo(nz(a.getMarketValue())));
        sb.append("Varlıklar:\n");
        int n = 0;
        for (PortfolioHoldingResponse h : holdings) {
            if (n++ >= MAX_LINES) {
                sb.append("… (").append(holdings.size() - MAX_LINES).append(" varlık daha)\n");
                break;
            }
            BigDecimal mv = nz(h.getMarketValue());
            BigDecimal pl = nz(h.getProfitLoss());
            BigDecimal cost = nz(h.getTotalCost());
            String plPct = cost.signum() > 0
                    ? " (" + fmt(pl.multiply(BigDecimal.valueOf(100)).divide(cost, 1, RoundingMode.HALF_UP)) + "%)"
                    : "";
            sb.append("• ").append(h.getSymbol()).append(" [").append(typeTr(
                            h.getAssetType() != null ? h.getAssetType().name() : "DİĞER")).append("]: ")
                    .append(fmt(mv)).append(" TL, K/Z ").append(fmt(pl)).append(" TL").append(plPct).append("\n");
        }
        return sb.toString().trim();
    }

    private static boolean boolArg(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        return node != null && !node.isNull() && node.asBoolean(false);
    }

    private static String typeTr(String t) {
        return switch (t) {
            case "STOCK" -> "Hisse";
            case "CRYPTO" -> "Kripto";
            case "FX" -> "Döviz";
            case "GOLD" -> "Altın";
            case "COMMODITY" -> "Emtia";
            case "FUND" -> "Fon";
            case "BOND" -> "Tahvil";
            case "FUTURE" -> "Vadeli";
            default -> t;
        };
    }

    private static String text(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        return node != null && !node.isNull() ? node.asText("").trim() : "";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
