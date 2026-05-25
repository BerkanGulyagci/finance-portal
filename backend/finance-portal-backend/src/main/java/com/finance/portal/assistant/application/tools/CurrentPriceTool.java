package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.stock.StockQueryService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bir varlığın ŞU ANKI fiyatını döndürür (FX / CRYPTO / GOLD / STOCK). Sembolü LLM üretir.
 */
@Component
public class CurrentPriceTool implements AssistantTool {

    private final MarketFxService marketFxService;
    private final CryptoMarketService cryptoMarketService;
    private final GoldMarketService goldMarketService;
    private final StockQueryService stockQueryService;
    private final SilverMarketService silverMarketService;
    private final YahooCommodityService yahooCommodityService;

    public CurrentPriceTool(MarketFxService marketFxService,
                            CryptoMarketService cryptoMarketService,
                            GoldMarketService goldMarketService,
                            StockQueryService stockQueryService,
                            SilverMarketService silverMarketService,
                            YahooCommodityService yahooCommodityService) {
        this.marketFxService = marketFxService;
        this.cryptoMarketService = cryptoMarketService;
        this.goldMarketService = goldMarketService;
        this.stockQueryService = stockQueryService;
        this.silverMarketService = silverMarketService;
        this.yahooCommodityService = yahooCommodityService;
    }

    @Override
    public String name() {
        return "get_current_price";
    }

    @Override
    public String description() {
        return "Bir varlığın ŞU ANKI fiyatı. asset_type: FX, CRYPTO, GOLD, STOCK, COMMODITY. "
                + "Sembol kuralları sistem promptunda; gümüş için COMMODITY + GUMUS.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "asset_type", Map.of(
                                "type", "string",
                                "enum", List.of("FX", "CRYPTO", "GOLD", "STOCK", "COMMODITY"),
                                "description", "Varlık türü"),
                        "symbol", Map.of(
                                "type", "string",
                                "description", "Açıklamadaki sembol kuralına göre sembol")),
                "required", List.of("asset_type", "symbol"));
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        String type = text(args, "asset_type").toUpperCase(Locale.ROOT);
        String symbol = text(args, "symbol").trim().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            return "Sembol belirtilmedi.";
        }
        try {
            return switch (type) {
                case "FX" -> fx(symbol);
                case "CRYPTO" -> crypto(symbol);
                case "GOLD" -> gold(symbol);
                case "STOCK" -> stock(symbol);
                case "COMMODITY" -> commodity(symbol);
                default -> "Desteklenmeyen tür: " + type;
            };
        } catch (Exception e) {
            return symbol + " için anlık fiyat alınamadı (" + type + ").";
        }
    }

    private String fx(String symbol) {
        FxLatestRates latest = marketFxService.getTcmbLatestRates(symbol);
        FxRateItem rate = latest.getRates().stream()
                .filter(r -> symbol.equalsIgnoreCase(r.getSymbol()))
                .findFirst().orElse(null);
        if (rate == null || rate.getSell() == null) {
            return symbol + " için TCMB kuru bulunamadı.";
        }
        int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
        BigDecimal sell = rate.getSell();
        if (unit > 1) {
            sell = sell.divide(BigDecimal.valueOf(unit), 4, RoundingMode.HALF_UP);
        }
        return "1 " + symbol + " = " + fmt(sell) + " TL (TCMB satış kuru, " + latest.getAsOf() + ").";
    }

    private String crypto(String symbol) {
        CryptoMarketItem item = cryptoMarketService.findBySymbol(symbol);
        if (item == null || item.getCurrentPrice() == null) {
            return symbol + " için kripto fiyatı bulunamadı.";
        }
        String name = item.getName() != null ? item.getName() : symbol;
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (").append(symbol).append(") = ").append(fmt(item.getCurrentPrice())).append(" TL");
        if (item.getPriceChangePercentage24h() != null) {
            sb.append(", son 24 saat ").append(fmt(item.getPriceChangePercentage24h())).append("%");
        }
        return sb.append(".").toString();
    }

    private String gold(String symbol) {
        GoldSpotResponse s = goldMarketService.getSpotGold();
        BigDecimal price = switch (symbol) {
            case "GRAM" -> s.getGramGoldTry();
            case "GOLD", "ONS" -> s.getOnsTry();
            case "CEYREK" -> s.getQuarterGoldTry();
            case "YARIM" -> s.getHalfGoldTry();
            case "TAM", "ZIYNET" -> s.getZiynetGoldTry();
            case "CUMHUR", "ATA" -> s.getRepublicGoldTry();
            default -> null;
        };
        if (price == null) {
            return "Altın türü tanınmadı veya fiyat yok: " + symbol;
        }
        String label = switch (symbol) {
            case "GRAM" -> "Gram altın";
            case "GOLD", "ONS" -> "Ons altın";
            case "CEYREK" -> "Çeyrek altın";
            case "YARIM" -> "Yarım altın";
            case "TAM", "ZIYNET" -> "Tam altın";
            case "CUMHUR", "ATA" -> "Cumhuriyet altını";
            default -> symbol;
        };
        return label + " = " + fmt(price) + " TL.";
    }

    private String stock(String symbol) {
        StockSummary s = stockQueryService.getStockSummary(symbol);
        if (s == null || s.getPrice() == null) {
            return symbol + " için BIST fiyatı bulunamadı (yalnız BIST hisseleri desteklenir).";
        }
        String cur = s.getCurrency() != null ? s.getCurrency() : "TRY";
        StringBuilder sb = new StringBuilder(symbol.replace(".IS", "") + " = " + fmt(s.getPrice()) + " " + cur);
        if (s.getChangePercent() != null) {
            sb.append(", günlük ").append(fmt(s.getChangePercent())).append("%");
        }
        return sb.append(".").toString();
    }

    private String commodity(String symbol) {
        // Gümüş özel: BIST gram TL. Diğerleri Yahoo emtia (NG=F, CL=F, GC=F…).
        if (symbol.equals("GUMUS") || symbol.equals("GÜMÜŞ") || symbol.equals("SILVER")
                || symbol.startsWith("SILVER:")) {
            SilverSpotResponse s = silverMarketService.getSpotSilver();
            BigDecimal p = s.getSilverGramTry() != null ? s.getSilverGramTry() : s.getSilverGramCloseTry();
            if (p == null) {
                return "Gümüş fiyatı bulunamadı.";
            }
            return "Gram gümüş = " + fmt(p) + " TL.";
        }
        CommoditySpotDto spot = yahooCommodityService.getSpot(symbol);
        if (spot == null || spot.getDisplayPrice() == null) {
            return symbol + " için emtia fiyatı bulunamadı.";
        }
        String cur = spot.getDisplayCurrency() != null ? spot.getDisplayCurrency() : "USD";
        String name = spot.getDisplayNameTr() != null ? spot.getDisplayNameTr()
                : (spot.getDisplayNameEn() != null ? spot.getDisplayNameEn() : symbol);
        return name + " = " + fmt(spot.getDisplayPrice()) + " " + cur + ".";
    }

    private static String fmt(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String text(JsonNode args, String field) {
        JsonNode n = args != null ? args.get(field) : null;
        return n != null && !n.isNull() ? n.asText("") : "";
    }
}
