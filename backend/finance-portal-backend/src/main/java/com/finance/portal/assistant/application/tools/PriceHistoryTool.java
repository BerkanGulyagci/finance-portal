package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Bir varlığın dönemsel performans + teknik özetini döndürür (getiri, min/max, MA20/MA50, RSI).
 * LLM bunu yorumlar ("trend pozitif olabilir" vb.) ama kesin al/sat tavsiyesi vermez.
 */
@Component
public class PriceHistoryTool implements AssistantTool {

    private final PortfolioHistoricalPricePort historicalPricePort;

    public PriceHistoryTool(PortfolioHistoricalPricePort historicalPricePort) {
        this.historicalPricePort = historicalPricePort;
    }

    @Override
    public String name() {
        return "get_price_history";
    }

    @Override
    public String description() {
        return "Dönemsel performans + teknik özet (getiri %, min/max, MA20, MA50, RSI) — trend/RSI/'X dönemde "
                + "ne yaptı' soruları. asset_type: FX/CRYPTO/GOLD/STOCK/BOND/COMMODITY/FUND. range: 1M/3M/6M/1Y/5Y.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "asset_type", Map.of("type", "string",
                                "enum", List.of("FX", "CRYPTO", "GOLD", "STOCK", "BOND", "COMMODITY", "FUND"),
                                "description", "Varlık türü"),
                        "symbol", Map.of("type", "string", "description", "Sembol (get_current_price kuralları)"),
                        "range", Map.of("type", "string",
                                "enum", List.of("1M", "3M", "6M", "1Y", "5Y"),
                                "description", "Dönem (varsayılan 1Y)")),
                "required", List.of("asset_type", "symbol"));
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        String typeStr = text(args, "asset_type").toUpperCase(Locale.ROOT);
        String symbol = text(args, "symbol").trim();
        String range = text(args, "range").toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            return "Sembol gereklidir.";
        }
        AssetType type;
        try {
            type = AssetType.valueOf(typeStr);
        } catch (Exception e) {
            return "Geçersiz varlık türü: " + typeStr;
        }
        LocalDate today = LocalDate.now();
        LocalDate from = switch (range) {
            case "1M" -> today.minusMonths(1);
            case "3M" -> today.minusMonths(3);
            case "6M" -> today.minusMonths(6);
            case "5Y" -> today.minusYears(5);
            default -> today.minusYears(1);
        };

        try {
            Optional<NavigableMap<LocalDate, BigDecimal>> opt =
                    historicalPricePort.fetchDailyClosePrices(type, symbol, from, today);
            if (opt.isEmpty() || opt.get().size() < 2) {
                return symbol + " için yeterli tarihsel veri bulunamadı.";
            }
            List<BigDecimal> closes = new ArrayList<>(opt.get().values());
            int n = closes.size();
            BigDecimal first = closes.get(0);
            BigDecimal last = closes.get(n - 1);
            BigDecimal min = closes.stream().min(BigDecimal::compareTo).orElse(last);
            BigDecimal max = closes.stream().max(BigDecimal::compareTo).orElse(last);
            String ret = first.signum() > 0
                    ? fmt(last.subtract(first).multiply(BigDecimal.valueOf(100)).divide(first, 2, RoundingMode.HALF_UP))
                    : "?";
            BigDecimal ma20 = PortfolioMovingAverage.simpleMa(closes, 20);
            BigDecimal ma50 = PortfolioMovingAverage.simpleMa(closes, 50);
            double rsi = computeRsi(closes, 14);

            StringBuilder sb = new StringBuilder();
            sb.append(symbol).append(" (").append(range.isBlank() ? "1Y" : range).append("): ");
            sb.append("başlangıç ").append(fmt(first)).append(" TL, güncel ").append(fmt(last)).append(" TL, ");
            sb.append("dönem getirisi ").append(ret).append("%. ");
            sb.append("Dönem min ").append(fmt(min)).append(" / max ").append(fmt(max)).append(" TL. ");
            if (ma20 != null) {
                sb.append("MA20 ").append(fmt(ma20)).append(" (fiyat ")
                        .append(last.compareTo(ma20) >= 0 ? "üstünde" : "altında").append("). ");
            }
            if (ma50 != null) {
                sb.append("MA50 ").append(fmt(ma50)).append(" (fiyat ")
                        .append(last.compareTo(ma50) >= 0 ? "üstünde" : "altında").append("). ");
            }
            if (!Double.isNaN(rsi)) {
                sb.append("RSI(14) ").append(String.format(Locale.US, "%.1f", rsi)).append(".");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return symbol + " için teknik veri alınamadı.";
        }
    }

    /** Wilder RSI (period gün). Yeterli veri yoksa NaN. */
    private static double computeRsi(List<BigDecimal> closes, int period) {
        if (closes.size() <= period) {
            return Double.NaN;
        }
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double diff = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            if (diff >= 0) gain += diff; else loss -= diff;
        }
        double avgGain = gain / period, avgLoss = loss / period;
        for (int i = period + 1; i < closes.size(); i++) {
            double diff = closes.get(i).doubleValue() - closes.get(i - 1).doubleValue();
            avgGain = (avgGain * (period - 1) + Math.max(diff, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-diff, 0)) / period;
        }
        if (avgLoss == 0) {
            return 100.0;
        }
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private static String fmt(BigDecimal v) {
        return v.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String text(JsonNode args, String field) {
        JsonNode node = args != null ? args.get(field) : null;
        return node != null && !node.isNull() ? node.asText("") : "";
    }
}
