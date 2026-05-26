package com.finance.portal.market.application.bond.eurobond;

import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.bond.eurobond.model.EurobondSummary;
import com.finance.portal.market.application.bond.eurobond.model.HmbBond;
import com.finance.portal.market.application.bond.eurobond.port.BusinessInsiderBondPort;
import com.finance.portal.market.application.bond.eurobond.port.HmbIsinSource;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Eurobond servisi: HMB ISIN listesi + Business Insider canlı verisi. Liste/detay/grafik cache'lenir.
 * Liste cache miss'inde ISIN'ler paralel çekilir (BI'yi boğmamak için sınırlı havuz).
 */
@Service
public class EurobondService {

    private static final Logger log = LoggerFactory.getLogger(EurobondService.class);
    private static final String LIST_CACHE = "market.eurobond.list";
    private static final String DETAIL_CACHE = "market.eurobond.detail";

    private final BusinessInsiderBondPort bi;
    private final HmbIsinSource hmb;
    private final CacheManager cacheManager;
    private final MarketFxService marketFxService;
    private final ExecutorService executor;

    public EurobondService(BusinessInsiderBondPort bi, HmbIsinSource hmb, CacheManager cacheManager,
                           MarketFxService marketFxService) {
        this.bi = bi;
        this.hmb = hmb;
        this.cacheManager = cacheManager;
        this.marketFxService = marketFxService;
        this.executor = Executors.newFixedThreadPool(6, daemon());
    }

    @Cacheable(cacheNames = LIST_CACHE)
    public List<EurobondSummary> list() {
        List<HmbBond> bonds = hmb.bonds();
        // BI detaylarını paralel çek (ban-koruması client'ta serileştirir); BI kapalıysa null → HMB satırı.
        Map<String, EurobondDetail> details = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>(bonds.size());
        for (HmbBond hb : bonds) {
            futures.add(CompletableFuture.runAsync(() -> {
                EurobondDetail d = loadDetail(hb.isin());
                if (d != null) {
                    details.put(hb.isin(), d);
                }
            }, executor));
        }
        for (CompletableFuture<Void> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
                /* HMB satırına düş */
            }
        }
        List<EurobondSummary> out = new ArrayList<>(bonds.size());
        for (HmbBond hb : bonds) {
            out.add(merge(hb, details.get(hb.isin())));
        }
        long withBi = out.stream().filter(EurobondSummary::hasDetail).count();
        log.info("Eurobond listesi derlendi: {} satır ({} BI canlı, {} yedek)", out.size(), withBi, out.size() - withBi);
        return out;
    }

    /** Bir eurobond'un anlık fiyatı (BI detay başlığından; cache'li detayı kullanır). Yoksa null. */
    public BigDecimal currentPrice(String isin) {
        EurobondDetail d = detail(isin);
        return d != null ? d.getLastPrice() : null;
    }

    private static EurobondSummary merge(HmbBond hb, EurobondDetail d) {
        String currency = hb.currency() != null ? hb.currency() : (d != null ? d.getCurrency() : null);
        String coupon = hb.couponRate() != null ? hb.couponRate() + "%" : (d != null ? d.getCouponRate() : null);
        String maturity = hb.maturityDate() != null ? hb.maturityDate() : (d != null ? d.getMaturityDate() : null);
        return new EurobondSummary(
                hb.isin(),
                d != null ? d.getName() : null,
                d != null && d.getIssuer() != null ? d.getIssuer() : "T.C. Hazine",
                currency,
                coupon,
                maturity,
                hb.issueType(),
                d != null ? d.getLastPrice() : null,
                d != null ? d.getChangePercent() : null,
                d != null && d.getInstrumentId() > 0 ? d.getInstrumentId() : null,
                d != null);
    }

    @Cacheable(cacheNames = DETAIL_CACHE, key = "#isin", unless = "#result == null")
    public EurobondDetail detail(String isin) {
        return loadDetail(isin);
    }

    @Cacheable(cacheNames = "market.eurobond.chart", key = "#isin + ':' + #range", unless = "#result.isEmpty()")
    public List<EurobondChartPoint> chart(String isin, String range) {
        EurobondDetail d = detail(isin);
        if (d == null || d.getTkData() == null || d.getTkData().isBlank()) {
            return List.of();
        }
        return bi.fetchChart(d.getTkData(), fromDate(range), LocalDate.now());
    }

    /** Admin/zamanlı: HMB xlsx'inden ISIN listesini tazele ve eurobond cache'lerini boşalt. */
    public int refreshIsins(String xlsxUrl) {
        int n = hmb.refreshFromXlsx(xlsxUrl);
        evict(LIST_CACHE);
        evict(DETAIL_CACHE);
        return n;
    }

    public List<String> currentIsins() {
        return hmb.isins();
    }

    /** Ayda bir (21'i 06:00) en son bilinen xlsx URL'inden ISIN listesini yeniden çeker (varsa). */
    @Scheduled(cron = "${eurobond.isin-refresh-cron:0 0 6 21 * *}")
    @SchedulerLock(name = "eurobond-isin-refresh", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    void scheduledRefresh() {
        String url = hmb.lastXlsxUrl();
        if (url == null) {
            log.debug("Eurobond zamanlı tazeleme: kayıtlı xlsx URL yok, atlandı (admin'den ekleyin).");
            return;
        }
        try {
            refreshIsins(url);
        } catch (Exception e) {
            log.warn("Eurobond zamanlı ISIN tazeleme başarısız: {}", e.getMessage());
        }
    }

    private EurobondDetail loadDetail(String isin) {
        EurobondDetail d = bi.resolve(isin).flatMap(bi::fetchDetail).orElse(null);
        if (d != null && d.getLastPrice() != null) {
            // Canlı TCMB kuruyla TL karşılığını ekle (detay/portföy/izleme TL gösterimi + portföy TL hesabı).
            BigDecimal rate = fxRateToTry(d.getCurrency());
            if (rate != null) {
                d.setFxRate(rate);
                d.setLastPriceTry(d.getLastPrice().multiply(rate).setScale(4, RoundingMode.HALF_UP));
            }
        }
        return d;
    }

    /**
     * Tahvil döviz cinsi (USD/EUR/JPY) → TRY birim satış kuru (TCMB; JPY gibi birim>1 olanlar bölünür).
     * TRY veya kur yoksa sırasıyla 1 / null döner.
     */
    public BigDecimal fxRateToTry(String currency) {
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        try {
            String cur = currency.trim().toUpperCase(Locale.ROOT);
            FxLatestRates latest = marketFxService.getTcmbLatestRates(cur);
            FxRateItem rate = latest.getRates().stream()
                    .filter(r -> cur.equalsIgnoreCase(r.getSymbol()))
                    .findFirst()
                    .orElse(null);
            if (rate == null) {
                return null;
            }
            BigDecimal sell = rate.getSell() != null ? rate.getSell() : rate.getBuy();
            if (sell == null) {
                return null;
            }
            int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
            return unit > 1
                    ? sell.divide(BigDecimal.valueOf(unit), 8, RoundingMode.HALF_UP)
                    : sell;
        } catch (Exception e) {
            log.debug("Eurobond FX kuru alınamadı ({}): {}", currency, e.getMessage());
            return null;
        }
    }

    private static LocalDate fromDate(String range) {
        LocalDate now = LocalDate.now();
        if (range == null) {
            return now.minusYears(1);
        }
        return switch (range.toUpperCase(Locale.ROOT)) {
            case "1M" -> now.minusMonths(1);
            case "3M" -> now.minusMonths(3);
            case "6M" -> now.minusMonths(6);
            case "YTD" -> LocalDate.of(now.getYear(), 1, 1);
            case "1Y" -> now.minusYears(1);
            case "3Y" -> now.minusYears(3);
            case "5Y" -> now.minusYears(5);
            case "MAX", "ALL" -> now.minusYears(30);
            default -> now.minusYears(1);
        };
    }

    private void evict(String cache) {
        Cache c = cacheManager.getCache(cache);
        if (c != null) {
            c.clear();
        }
    }

    private static ThreadFactory daemon() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "eurobond-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
