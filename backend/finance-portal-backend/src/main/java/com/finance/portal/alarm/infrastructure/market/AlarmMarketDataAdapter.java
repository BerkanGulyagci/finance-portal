package com.finance.portal.alarm.infrastructure.market;

import com.finance.portal.alarm.application.model.AlarmMarketSnapshot;
import com.finance.portal.alarm.application.port.AlarmMarketDataPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Alarm değerlendirmesi için anlık metrikleri sağlar. Mevcut market servislerini yeniden kullanır:
 * <ul>
 *   <li>STOCK / FUTURE → {@link StockQueryService} (fiyat + değişim% + hacim)</li>
 *   <li>CRYPTO → {@link CryptoMarketService} (fiyat + 24s değişim% + hacim)</li>
 *   <li>FX / FUND → {@link AssetPriceQueryService} (yalnız fiyat)</li>
 *   <li>GOLD → {@link GoldMarketService} (sembole göre TRY fiyat + değişim%)</li>
 *   <li>COMMODITY → {@link YahooCommodityService} (fiyat + değişim% + hacim)</li>
 *   <li>BOND → {@link EvdsBondService} (gösterge değeri + günlük değişim%)</li>
 * </ul>
 * Veri çekilemezse {@code null} döner → o alarm o değerlendirme turunda atlanır.
 */
@Component
public class AlarmMarketDataAdapter implements AlarmMarketDataPort {

    private static final Logger log = LoggerFactory.getLogger(AlarmMarketDataAdapter.class);

    private final StockQueryService stockQueryService;
    private final CryptoMarketService cryptoMarketService;
    private final AssetPriceQueryService assetPriceQueryService;
    private final GoldMarketService goldMarketService;
    private final YahooCommodityService yahooCommodityService;
    private final EvdsBondService evdsBondService;
    private final ViopService viopService;
    private final SilverMarketService silverMarketService;
    private final PreciousMetalService preciousMetalService;

    public AlarmMarketDataAdapter(StockQueryService stockQueryService,
                                  CryptoMarketService cryptoMarketService,
                                  AssetPriceQueryService assetPriceQueryService,
                                  GoldMarketService goldMarketService,
                                  YahooCommodityService yahooCommodityService,
                                  EvdsBondService evdsBondService,
                                  ViopService viopService,
                                  SilverMarketService silverMarketService,
                                  PreciousMetalService preciousMetalService) {
        this.stockQueryService = stockQueryService;
        this.cryptoMarketService = cryptoMarketService;
        this.assetPriceQueryService = assetPriceQueryService;
        this.goldMarketService = goldMarketService;
        this.yahooCommodityService = yahooCommodityService;
        this.evdsBondService = evdsBondService;
        this.viopService = viopService;
        this.silverMarketService = silverMarketService;
        this.preciousMetalService = preciousMetalService;
    }

    @Override
    public AlarmMarketSnapshot probe(AssetType assetType, String symbol) {
        if (assetType == null || symbol == null || symbol.isBlank()) {
            return null;
        }
        try {
            return switch (assetType) {
                case STOCK -> fromStock(symbol);
                case FUTURE -> fromFuture(symbol);
                case CRYPTO -> fromCrypto(symbol);
                case FX, FUND -> fromAssetPriceQuery(assetType, symbol);
                case GOLD -> fromGold(symbol);
                case COMMODITY -> fromCommodity(symbol);
                case BOND -> fromBond(symbol);
            };
        } catch (Exception ex) {
            log.debug("Alarm probe failed for {} {}: {}", assetType, symbol, ex.getMessage());
            return null;
        }
    }

    private AlarmMarketSnapshot fromStock(String symbol) {
        StockSummary s = stockQueryService.getStockSummary(symbol.toUpperCase(Locale.ROOT));
        if (s == null) {
            return null;
        }
        BigDecimal volume = s.getVolume() != null ? BigDecimal.valueOf(s.getVolume()) : null;
        return new AlarmMarketSnapshot(s.getPrice(), s.getChangePercent(), volume);
    }

    /** VİOP kontratları (BIST) → ViopService; Yahoo vadeli (ES=F vb.) → stock summary fallback. */
    private AlarmMarketSnapshot fromFuture(String symbol) {
        Optional<ViopContract> viop = viopService.findMatchingContract(symbol);
        if (viop.isPresent()) {
            ViopContract c = viop.get();
            BigDecimal price = parseTrNumber(c.getLastPrice());
            if (price != null) {
                return new AlarmMarketSnapshot(price, parseTrNumber(c.getChangePercent()), null);
            }
        }
        return fromStock(symbol);
    }

    private AlarmMarketSnapshot fromCrypto(String symbol) {
        CryptoMarketItem c = cryptoMarketService.findBySymbol(symbol);
        if (c == null) {
            return null;
        }
        return new AlarmMarketSnapshot(c.getCurrentPrice(), c.getPriceChangePercentage24h(), c.getTotalVolume());
    }

    private AlarmMarketSnapshot fromAssetPriceQuery(AssetType assetType, String symbol) {
        AssetPriceSnapshot s = assetPriceQueryService.getCurrentPrice(assetType, symbol);
        if (s == null) {
            return null;
        }
        return new AlarmMarketSnapshot(s.getPrice(), null, null);
    }

    private AlarmMarketSnapshot fromGold(String symbol) {
        GoldSpotResponse g = goldMarketService.getSpotGold();
        if (g == null) {
            return null;
        }
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        BigDecimal price = switch (s) {
            case "GOLD", "ONS" -> g.getOnsTry();
            case "GRAM" -> g.getGramGoldTry();
            case "CEYREK" -> g.getQuarterGoldTry();
            case "YARIM" -> g.getHalfGoldTry();
            case "ZIYNET", "TAM" -> g.getZiynetGoldTry();
            case "CUMHUR", "ATA", "CUMHURIYET" -> g.getRepublicGoldTry();
            case "14AYAR", "AYAR14" -> g.getFourteenKBraceletTry();
            case "22AYAR", "AYAR22" -> g.getTwentyTwoKBraceletTry();
            default -> g.getGramGoldTry();
        };
        BigDecimal changePercent = ("GOLD".equals(s) || "ONS".equals(s))
                ? g.getOnsChangePercent() : g.getChangePercent();
        return new AlarmMarketSnapshot(price, changePercent, null);
    }

    private AlarmMarketSnapshot fromCommodity(String symbol) {
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("SILVER")) {
            return fromSilver(s);
        }
        if (s.startsWith("PLATINUM") || s.startsWith("PALLADIUM")) {
            return fromPreciousMetal(s);
        }
        CommoditySpotDto c = yahooCommodityService.getSpot(symbol);
        if (c == null) {
            return null;
        }
        BigDecimal price = c.getDisplayPrice() != null ? c.getDisplayPrice() : c.getRawPrice();
        BigDecimal volume = c.getVolume() != null ? BigDecimal.valueOf(c.getVolume()) : null;
        return new AlarmMarketSnapshot(price, c.getChangePercent(), volume);
    }

    /** SILVER:GRAM_TRY / SILVER:KG_TRY / SILVER:USD_ONS (BIST gümüş — gümüş sayfasıyla aynı kaynak). */
    private AlarmMarketSnapshot fromSilver(String s) {
        SilverSpotResponse sp = silverMarketService.getSpotSilver();
        if (sp == null) {
            return null;
        }
        BigDecimal price = switch (catOf(s)) {
            case "KG_TRY" -> sp.getCloseTryKg();
            case "USD_ONS" -> sp.getSilverUsdOns();
            default -> sp.getSilverGramTry();
        };
        return new AlarmMarketSnapshot(price, null, null);
    }

    // PLATINUM:* / PALLADIUM:* (BIST — kıymetli maden sayfasıyla aynı kaynak).
    private AlarmMarketSnapshot fromPreciousMetal(String s) {
        PreciousMetalType type = s.startsWith("PLATINUM") ? PreciousMetalType.PLATINUM : PreciousMetalType.PALLADIUM;
        PreciousMetalSpotResponse sp = preciousMetalService.getSpot(type);
        if (sp == null) {
            return null;
        }
        BigDecimal price = switch (catOf(s)) {
            case "KG_TRY" -> sp.getTryKg();
            case "USD_ONS" -> sp.getUsdOns();
            case "EUR_ONS" -> sp.getEurOns();
            default -> sp.getTryGram();
        };
        return new AlarmMarketSnapshot(price, null, null);
    }

    /** "SILVER:KG_TRY" → "KG_TRY"; iki nokta yoksa "GRAM_TRY". */
    private static String catOf(String s) {
        int i = s.indexOf(':');
        return (i >= 0 && i + 1 < s.length()) ? s.substring(i + 1) : "GRAM_TRY";
    }

    private AlarmMarketSnapshot fromBond(String symbol) {
        EvdsBondInstrument b = evdsBondService.getEvdsBondDetail(symbol);
        if (b == null) {
            return null;
        }
        return new AlarmMarketSnapshot(b.getIndicatorValue(), b.getDailyChangePercent(), null);
    }

    /** Türkçe biçimli sayıyı ("1.234,56", "%1,23") BigDecimal'e çevirir; çözülemezse null. */
    private static BigDecimal parseTrNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replace("%", "").replace(" ", "").trim();
        if (s.contains(",")) {
            // virgül ondalık → noktaları (binlik) at, virgülü noktaya çevir
            s = s.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
