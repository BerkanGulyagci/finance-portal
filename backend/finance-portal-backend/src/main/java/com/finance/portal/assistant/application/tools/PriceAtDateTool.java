package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Bir varlığın BELİRLİ BİR TARİHTEKİ (TL) kapanış fiyatını döndürür — "10 Ocak'ta gram altın kaç TL idi?".
 * Tüm türleri destekler (genel tarihsel fiyat adaptörü üzerinden); değer o güne ait veya en yakın önceki gün.
 */
@Component
public class PriceAtDateTool implements AssistantTool {

    private final PortfolioHistoricalPricePort historicalPricePort;

    public PriceAtDateTool(PortfolioHistoricalPricePort historicalPricePort) {
        this.historicalPricePort = historicalPricePort;
    }

    @Override
    public String name() {
        return "get_price_at_date";
    }

    @Override
    public String description() {
        return "Bir varlığın BELİRLİ BİR TARİHTEKİ TL kapanışı. asset_type: "
                + "FX/CRYPTO/GOLD/STOCK/BOND/COMMODITY/FUND/FUTURE. date=YYYY-MM-DD. "
                + "Sembol kuralları sistem promptunda; veri yoksa en yakın önceki iş günü.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "asset_type", Map.of(
                                "type", "string",
                                "enum", List.of("FX", "CRYPTO", "GOLD", "STOCK", "BOND", "COMMODITY", "FUND", "FUTURE"),
                                "description", "Varlık türü"),
                        "symbol", Map.of("type", "string", "description", "Açıklamadaki kurala göre sembol"),
                        "date", Map.of("type", "string", "description", "Tarih, YYYY-MM-DD")),
                "required", List.of("asset_type", "symbol", "date"));
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        String typeStr = text(args, "asset_type").toUpperCase(Locale.ROOT);
        String symbol = text(args, "symbol").trim();
        String dateStr = text(args, "date").trim();
        if (symbol.isEmpty() || dateStr.isEmpty()) {
            return "Sembol ve tarih gereklidir.";
        }
        AssetType type;
        LocalDate date;
        try {
            type = AssetType.valueOf(typeStr);
        } catch (Exception e) {
            return "Geçersiz varlık türü: " + typeStr;
        }
        try {
            date = LocalDate.parse(dateStr.substring(0, Math.min(10, dateStr.length())));
        } catch (Exception e) {
            return "Geçersiz tarih: " + dateStr + " (YYYY-MM-DD bekleniyor).";
        }
        if (date.isAfter(LocalDate.now())) {
            return "Gelecek bir tarih için fiyat verilemez.";
        }

        try {
            // Hafta sonu/tatil için 10 günlük pencereden en yakın önceki kapanışı al.
            Optional<NavigableMap<LocalDate, BigDecimal>> series =
                    historicalPricePort.fetchDailyClosePrices(type, symbol, date.minusDays(10), date);
            if (series.isEmpty() || series.get().isEmpty()) {
                return symbol + " için " + date + " tarihine ait fiyat verisi bulunamadı.";
            }
            var entry = series.get().floorEntry(date);
            if (entry == null) {
                entry = series.get().lastEntry();
            }
            String when = entry.getKey().equals(date) ? date.toString() : (entry.getKey() + " (en yakın iş günü)");
            return symbol + " — " + when + " kapanışı ≈ " + entry.getValue().stripTrailingZeros().toPlainString() + " TL.";
        } catch (Exception e) {
            return symbol + " için tarihsel fiyat alınamadı.";
        }
    }

    private static String text(JsonNode args, String field) {
        JsonNode n = args != null ? args.get(field) : null;
        return n != null && !n.isNull() ? n.asText("") : "";
    }
}
