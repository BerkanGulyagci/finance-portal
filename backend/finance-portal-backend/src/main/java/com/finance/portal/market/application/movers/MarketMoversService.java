package com.finance.portal.market.application.movers;

import com.finance.portal.market.application.commodity.CommodityDto;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.currency.BankCurrencyRateDto;
import com.finance.portal.market.application.currency.BankCurrencyService;
import com.finance.portal.market.application.movers.model.MarketMover;
import com.finance.portal.market.application.movers.model.MoversCategory;
import com.finance.portal.market.application.stock.StockPageResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Piyasanın hareketlileri — günün en çok yükselen / düşen enstrümanları (sahip olunanlardan
 * BAĞIMSIZ, tüm piyasa taranır). Veri zaten cache'li liste servislerinden okunur (yeni dış çağrı yok).
 *
 * <p>Kategoriler: Kripto (CoinGecko 24s), BIST Hisse (günlük %), Döviz (banka kurları günlük %),
 * Emtia (Yahoo spot günlük %). Fonlar HARİÇ (günde tek NAV + hafta sonu 0); tahvil de hariç
 * (günlük neredeyse sabit).
 *
 * <p>Dönen tip Redis'e cache'lenir; bu yüzden {@link MarketMover}/{@link MoversCategory} record
 * DEĞİL {@code @JsonCreator}'lı sınıftır ve tüm listeler {@link ArrayList} olarak üretilir
 * (immutable collection'lar GenericJackson2 round-trip'inde patlıyordu).
 */
@Service
public class MarketMoversService {

    private static final Logger log = LoggerFactory.getLogger(MarketMoversService.class);
    private static final int STOCK_PAGE_SIZE = 20; // warm-up ile aynı → cache HIT

    private final CryptoMarketService cryptoMarketService;
    private final StockQueryService stockQueryService;
    private final BankCurrencyService bankCurrencyService;
    private final YahooCommodityService commodityService;

    public MarketMoversService(CryptoMarketService cryptoMarketService,
                               StockQueryService stockQueryService,
                               BankCurrencyService bankCurrencyService,
                               YahooCommodityService commodityService) {
        this.cryptoMarketService = cryptoMarketService;
        this.stockQueryService = stockQueryService;
        this.bankCurrencyService = bankCurrencyService;
        this.commodityService = commodityService;
    }

    /** Tüm kategoriler için top-N yükselen + top-N düşen. Cache 120 sn. */
    @Cacheable(cacheNames = "market.movers", key = "'all:' + #limit")
    public List<MoversCategory> getMovers(int limit) {
        return new ArrayList<>(List.of(
                cryptoMovers(limit),
                stockMovers(limit),
                fxMovers(limit),
                commodityMovers(limit)));
    }

    // ── Kripto ──────────────────────────────────────────────────────────────
    private MoversCategory cryptoMovers(int limit) {
        List<MarketMover> all = new ArrayList<>();
        try {
            for (CryptoMarketItem c : cryptoMarketService.getAllCoins("try")) {
                if (c.getPriceChangePercentage24h() == null || c.getCurrentPrice() == null) continue;
                all.add(new MarketMover(
                        "CRYPTO", c.getId(),
                        c.getSymbol() != null ? c.getSymbol().toUpperCase(Locale.ROOT) : c.getId(),
                        c.getName(), c.getCurrentPrice(), "TRY",
                        c.getPriceChangePercentage24h(), c.getImage()));
            }
        } catch (Exception e) {
            log.warn("[Movers] kripto listesi alınamadı: {}", e.getMessage());
        }
        return new MoversCategory("crypto", "Kripto", topN(all, limit, true), topN(all, limit, false));
    }

    // ── BIST Hisse ──────────────────────────────────────────────────────────
    private MoversCategory stockMovers(int limit) {
        List<MarketMover> all = new ArrayList<>();
        try {
            StockPageResponse first = stockQueryService.getPagedStockSummaries(0, STOCK_PAGE_SIZE);
            addStocks(all, first.getContent());
            int totalPages = first.getTotalPages();
            for (int p = 1; p < totalPages; p++) {
                addStocks(all, stockQueryService.getPagedStockSummaries(p, STOCK_PAGE_SIZE).getContent());
            }
        } catch (Exception e) {
            log.warn("[Movers] hisse listesi alınamadı: {}", e.getMessage());
        }
        return new MoversCategory("stock", "BIST Hisse", topN(all, limit, true), topN(all, limit, false));
    }

    private void addStocks(List<MarketMover> out, List<StockSummary> content) {
        if (content == null) return;
        for (StockSummary s : content) {
            if (s.getChangePercent() == null || s.getSymbol() == null) continue;
            out.add(new MarketMover("STOCK", s.getSymbol(), s.getSymbol(), s.getName(),
                    s.getPrice(), "TRY", s.getChangePercent(), null));
        }
    }

    // ── Döviz (banka kurları, currency koduna göre ortalama günlük %) ────────
    private MoversCategory fxMovers(int limit) {
        Map<String, List<BankCurrencyRateDto>> byCode = new LinkedHashMap<>();
        try {
            for (BankCurrencyRateDto r : bankCurrencyService.getAllBankRates()) {
                if (r.getCurrencyCode() == null) continue;
                byCode.computeIfAbsent(r.getCurrencyCode().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(r);
            }
        } catch (Exception e) {
            log.warn("[Movers] döviz kurları alınamadı: {}", e.getMessage());
        }
        List<MarketMover> all = new ArrayList<>();
        for (Map.Entry<String, List<BankCurrencyRateDto>> e : byCode.entrySet()) {
            double sumPct = 0; int nPct = 0;
            double sumRate = 0; int nRate = 0;
            String name = null;
            for (BankCurrencyRateDto r : e.getValue()) {
                if (r.getDailyChangePercent() != null) { sumPct += r.getDailyChangePercent(); nPct++; }
                if (r.getLastRate() != null) { sumRate += r.getLastRate(); nRate++; }
                if (name == null && r.getCurrencyName() != null) name = r.getCurrencyName();
            }
            if (nPct == 0) continue; // günlük değişim yoksa movers'a giremez
            BigDecimal pct = BigDecimal.valueOf(sumPct / nPct).setScale(2, RoundingMode.HALF_UP);
            BigDecimal price = nRate > 0
                    ? BigDecimal.valueOf(sumRate / nRate).setScale(4, RoundingMode.HALF_UP) : null;
            all.add(new MarketMover("FX", e.getKey(), e.getKey(),
                    name != null ? name : e.getKey(), price, "TRY", pct, null));
        }
        return new MoversCategory("fx", "Döviz", topN(all, limit, true), topN(all, limit, false));
    }

    // ── Emtia (Yahoo spot, günlük %) ─────────────────────────────────────────
    private MoversCategory commodityMovers(int limit) {
        List<MarketMover> all = new ArrayList<>();
        try {
            for (CommodityDto cd : commodityService.listEnabledCommodities()) {
                try {
                    CommoditySpotDto spot = commodityService.getSpot(cd.getSymbol());
                    if (spot == null || spot.getChangePercent() == null) continue;
                    String display = cd.getDisplayNameTr() != null ? cd.getDisplayNameTr()
                            : (cd.getDisplayNameEn() != null ? cd.getDisplayNameEn() : cd.getSymbol());
                    String cur = spot.getDisplayCurrency() != null ? spot.getDisplayCurrency() : "USD";
                    all.add(new MarketMover("COMMODITY", cd.getSymbol(), display, display,
                            spot.getDisplayPrice(), cur, spot.getChangePercent(), null));
                } catch (Exception ignore) {
                    // tek emtia hatası tüm kategoriyi düşürmesin
                }
            }
        } catch (Exception e) {
            log.warn("[Movers] emtia listesi alınamadı: {}", e.getMessage());
        }
        return new MoversCategory("commodity", "Emtia", topN(all, limit, true), topN(all, limit, false));
    }

    /** gainers=true → %>0, azalan sırada top n; aksi → %<0, artan sırada top n. ArrayList döner. */
    private List<MarketMover> topN(List<MarketMover> items, int n, boolean gainers) {
        Comparator<MarketMover> byChange = Comparator.comparing(MarketMover::getChangePercent);
        return items.stream()
                .filter(m -> m.getChangePercent() != null
                        && (gainers ? m.getChangePercent().signum() > 0 : m.getChangePercent().signum() < 0))
                .sorted(gainers ? byChange.reversed() : byChange)
                .limit(n)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Hacim liderleri — günlük işlem hacmi EN YÜKSEK enstrümanlar (tip-bazlı).
    //  Birimler tipler arası farklı (hisse/kripto TL, emtia/ons USD) → sekmeli,
    //  her tip kendi içinde sıralanır. Hacmi olmayan tipler (döviz/fon/tahvil) yok.
    // ════════════════════════════════════════════════════════════════════════

    /** Tüm kategoriler için günlük hacmi en yüksek top-N. Cache 120 sn. */
    @Cacheable(cacheNames = "market.volumeLeaders", key = "'all:' + #limit")
    public List<MoversCategory> getVolumeLeaders(int limit) {
        return new ArrayList<>(List.of(
                cryptoVolumeLeaders(limit),
                stockVolumeLeaders(limit),
                commodityVolumeLeaders(limit)));
    }

    private MoversCategory cryptoVolumeLeaders(int limit) {
        List<MarketMover> all = new ArrayList<>();
        try {
            for (CryptoMarketItem c : cryptoMarketService.getAllCoins("try")) {
                if (c.getTotalVolume() == null || c.getCurrentPrice() == null) continue;
                all.add(new MarketMover(
                        "CRYPTO", c.getId(),
                        c.getSymbol() != null ? c.getSymbol().toUpperCase(Locale.ROOT) : c.getId(),
                        c.getName(), c.getCurrentPrice(), "TRY",
                        c.getPriceChangePercentage24h(), c.getImage(), c.getTotalVolume()));
            }
        } catch (Exception e) {
            log.warn("[VolumeLeaders] kripto listesi alınamadı: {}", e.getMessage());
        }
        return new MoversCategory("crypto", "Kripto", topByVolume(all, limit), new ArrayList<>());
    }

    private MoversCategory stockVolumeLeaders(int limit) {
        List<MarketMover> all = new ArrayList<>();
        try {
            StockPageResponse first = stockQueryService.getPagedStockSummaries(0, STOCK_PAGE_SIZE);
            addStockVolumes(all, first.getContent());
            int totalPages = first.getTotalPages();
            for (int p = 1; p < totalPages; p++) {
                addStockVolumes(all, stockQueryService.getPagedStockSummaries(p, STOCK_PAGE_SIZE).getContent());
            }
        } catch (Exception e) {
            log.warn("[VolumeLeaders] hisse listesi alınamadı: {}", e.getMessage());
        }
        return new MoversCategory("stock", "BIST Hisse", topByVolume(all, limit), new ArrayList<>());
    }

    private void addStockVolumes(List<MarketMover> out, List<StockSummary> content) {
        if (content == null) return;
        for (StockSummary s : content) {
            if (s.getVolume() == null || s.getSymbol() == null) continue;
            out.add(new MarketMover("STOCK", s.getSymbol(), s.getSymbol(), s.getName(),
                    s.getPrice(), "TRY", s.getChangePercent(), null,
                    BigDecimal.valueOf(s.getVolume())));
        }
    }

    private MoversCategory commodityVolumeLeaders(int limit) {
        List<MarketMover> all = new ArrayList<>();
        try {
            for (CommodityDto cd : commodityService.listEnabledCommodities()) {
                try {
                    CommoditySpotDto spot = commodityService.getSpot(cd.getSymbol());
                    if (spot == null || spot.getVolume() == null) continue;
                    String display = cd.getDisplayNameTr() != null ? cd.getDisplayNameTr()
                            : (cd.getDisplayNameEn() != null ? cd.getDisplayNameEn() : cd.getSymbol());
                    String cur = spot.getDisplayCurrency() != null ? spot.getDisplayCurrency() : "USD";
                    all.add(new MarketMover("COMMODITY", cd.getSymbol(), display, display,
                            spot.getDisplayPrice(), cur, spot.getChangePercent(), null,
                            BigDecimal.valueOf(spot.getVolume())));
                } catch (Exception ignore) {
                    // tek emtia hatası tüm kategoriyi düşürmesin
                }
            }
        } catch (Exception e) {
            log.warn("[VolumeLeaders] emtia listesi alınamadı: {}", e.getMessage());
        }
        return new MoversCategory("commodity", "Emtia", topByVolume(all, limit), new ArrayList<>());
    }

    /** Hacme göre azalan sırada top-n (volume null/<=0 elenir). ArrayList döner. */
    private List<MarketMover> topByVolume(List<MarketMover> items, int n) {
        return items.stream()
                .filter(m -> m.getVolume() != null && m.getVolume().signum() > 0)
                .sorted(Comparator.comparing(MarketMover::getVolume).reversed())
                .limit(n)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
