package com.finance.portal.market.application.bond.eurobond;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.AccruedInterestCalculator;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.bond.eurobond.model.EurobondPriceAt;
import com.finance.portal.market.application.bond.eurobond.model.EurobondSummary;
import com.finance.portal.market.application.bond.eurobond.model.HmbBond;
import com.finance.portal.market.application.bond.eurobond.port.BusinessInsiderBondPort;
import com.finance.portal.market.application.bond.eurobond.port.HmbIsinSource;
import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    /** Liste/detay (hızlı) veri LKG ömrü — BI çökerse en fazla bu kadar eski ISIN/detay servis edilir. */
    private static final Duration EUROBOND_LIST_LKG_TTL = Duration.ofDays(3);
    /** Grafik (yavaş, ~değişmeyen geçmiş) veri LKG ömrü. */
    private static final Duration EUROBOND_CHART_LKG_TTL = Duration.ofDays(14);

    private final BusinessInsiderBondPort bi;
    private final HmbIsinSource hmb;
    private final CacheManager cacheManager;
    private final MarketFxService marketFxService;
    private final LastKnownGoodCache lkg;
    private final ExecutorService executor;

    public EurobondService(BusinessInsiderBondPort bi, HmbIsinSource hmb, CacheManager cacheManager,
                           MarketFxService marketFxService, LastKnownGoodCache lkg) {
        this.bi = bi;
        this.hmb = hmb;
        this.cacheManager = cacheManager;
        this.marketFxService = marketFxService;
        this.lkg = lkg;
        this.executor = Executors.newFixedThreadPool(6, daemon());
    }

    @Cacheable(cacheNames = LIST_CACHE)
    public List<EurobondSummary> list() {
        // HMB ISIN künyesi — kaynak çökerse son başarılı listeyi servis et (BI detayları ayrıca LKG'li).
        List<HmbBond> bonds = lkg.resilient("eurobond.hmb.bonds", EUROBOND_LIST_LKG_TTL,
                new TypeReference<List<HmbBond>>() {}, hmb::bonds);
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
        // Grafik serisi ~değişmez → uzun LKG; BI çökerse son başarılı seriyi servis et.
        return lkg.resilient("eurobond.bi.chart:" + isin + ":" + range, EUROBOND_CHART_LKG_TTL,
                new TypeReference<List<EurobondChartPoint>>() {},
                () -> bi.fetchChart(d.getTkData(), fromDate(range), LocalDate.now()));
    }

    /** Admin/zamanlı: HMB xlsx'inden ISIN listesini tazele ve eurobond cache'lerini boşalt. */
    public int refreshIsins(String xlsxUrl) {
        return refreshIsins(xlsxUrl, false);
    }

    /** {@link #refreshIsins(String)}; force=true admin override (URL filename keyword check'i bypass). */
    public int refreshIsins(String xlsxUrl, boolean force) {
        int n = hmb.refreshFromXlsx(xlsxUrl, force);
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
        // BI canlı detayı (resolve→fetchDetail) — BI çökerse son başarılı detayı servis et.
        // FX/TL zenginleştirmesi servis edilen değer üzerine her zaman yeniden uygulanır (güncel kur).
        EurobondDetail d = lkg.resilient("eurobond.bi.detail:" + isin, EUROBOND_LIST_LKG_TTL,
                new TypeReference<EurobondDetail>() {},
                () -> bi.resolve(isin).flatMap(bi::fetchDetail).orElse(null));
        if (d != null && d.getLastPrice() != null) {
            // Canlı TCMB kuruyla TL karşılığını ekle (detay/portföy/izleme TL gösterimi + portföy TL hesabı).
            BigDecimal rate = fxRateToTry(d.getCurrency());
            if (rate != null) {
                d.setFxRate(rate);
                d.setLastPriceTry(d.getLastPrice().multiply(rate).setScale(4, RoundingMode.HALF_UP));
            }
            enrichAccruedInterest(d, rate);
        }
        return d;
    }

    /**
     * Birikmiş faiz + kirli fiyat (tahmini) alanlarını doldurur — yalnız BİLGİ amaçlı; mevcut
     * lastPrice/lastPriceTry değerlemesini DEĞİŞTİRMEZ. Kupon künyesi eksik/uygunsuzsa alanlar
     * null kalır (UI göstermez). Defensif: hesap herhangi bir nedenle patlarsa detay akışını
     * bozmadan sessizce atlanır (kirli fiyat opsiyonel ek bilgidir).
     */
    private void enrichAccruedInterest(EurobondDetail d, BigDecimal fxRate) {
        try {
            AccruedInterestCalculator.AccruedResult r =
                    AccruedInterestCalculator.compute(d, d.getLastPrice(), LocalDate.now());
            if (!r.available()) {
                return;
            }
            d.setAccruedInterest(r.accruedInterest());
            d.setDirtyPrice(r.dirtyPrice());
            d.setDayCountConvention(r.dayCount() != null ? r.dayCount().name() : null);
            if (r.dirtyPrice() != null && fxRate != null) {
                d.setDirtyPriceTry(r.dirtyPrice().multiply(fxRate).setScale(4, RoundingMode.HALF_UP));
            }
        } catch (RuntimeException e) {
            log.debug("Eurobond birikmiş faiz hesaplanamadı (atlandı) isin={}: {}",
                    d.getIsin(), e.getMessage());
        }
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

    /**
     * Bir eurobond'un {@code date} tarihindeki <b>kirli fiyat dökümü</b> (TL) — işlem ekleme
     * modalı autofill'i için. Temiz kote ({@link #chart}) ve birikmiş faiz
     * ({@link AccruedInterestCalculator}) <b>o tarihteki</b> TCMB kuruyla ({@link MarketFxService#getFxHistory})
     * TL'ye çevrilir (Model 1: tarihsel FX gömülü). Kupon konvansiyonu kaynakta olmadığı için
     * birikmiş faiz tahminidir. Veri yoksa {@link EurobondPriceAt#notFound()}.
     */
    public EurobondPriceAt priceAt(String isin, LocalDate date) {
        if (isin == null || date == null) {
            return EurobondPriceAt.notFound();
        }
        try {
            EurobondDetail d = detail(isin.trim().toUpperCase(Locale.ROOT));
            if (d == null) {
                return EurobondPriceAt.notFound();
            }
            String currency = d.getCurrency() != null ? d.getCurrency().toUpperCase(Locale.ROOT) : "USD";

            // 1) İstenen tarihteki temiz kote fiyatı (BI grafik serisinden floor); bugün/yakın için
            //    seri kapsamıyorsa güncel lastPrice'a düş.
            String range = priceAtRange(date);
            BigDecimal cleanQuote = quoteAtOrBefore(isin, range, date);
            if (cleanQuote == null) {
                cleanQuote = d.getLastPrice();
            }
            if (cleanQuote == null || cleanQuote.signum() <= 0) {
                return EurobondPriceAt.notFound();
            }

            // 2) İstenen tarihteki TCMB kuru (tarihsel seri floor); yoksa güncel kura düş.
            BigDecimal fx = fxAtOrBefore(currency, range, date);
            if (fx == null || fx.signum() <= 0) {
                fx = fxRateToTry(currency);
            }
            if (fx == null || fx.signum() <= 0) {
                return EurobondPriceAt.notFound();
            }

            // 3) O tarihteki birikmiş faiz (döviz, 100 nominal başına; tahmini).
            AccruedInterestCalculator.AccruedResult acc =
                    AccruedInterestCalculator.compute(d, cleanQuote, date);
            BigDecimal accruedQuote = acc.available() && acc.accruedInterest() != null
                    ? acc.accruedInterest() : BigDecimal.ZERO;
            String dcc = acc.available() && acc.dayCount() != null ? acc.dayCount().name() : null;

            // 4) TL'ye çevir (hepsi aynı o-gün kuruyla → tutarlı).
            BigDecimal cleanTry = cleanQuote.multiply(fx).setScale(6, RoundingMode.HALF_UP);
            BigDecimal accruedTry = accruedQuote.multiply(fx).setScale(6, RoundingMode.HALF_UP);
            BigDecimal dirtyTry = cleanTry.add(accruedTry).setScale(6, RoundingMode.HALF_UP);

            return new EurobondPriceAt(true, date, currency,
                    cleanTry, accruedTry, dirtyTry, cleanQuote, accruedQuote, dcc);
        } catch (RuntimeException e) {
            log.debug("Eurobond priceAt hesaplanamadı isin={} date={}: {}", isin, date, e.getMessage());
            return EurobondPriceAt.notFound();
        }
    }

    /** {@code date}'i kapsayacak en kısa BI/FX aralığı (frontend autofill ile aynı mantık). */
    private static String priceAtRange(LocalDate date) {
        long days = ChronoUnit.DAYS.between(date, LocalDate.now());
        if (days <= 0) return "1M";
        if (days <= 25) return "1M";
        if (days <= 80) return "3M";
        if (days <= 170) return "6M";
        if (days <= 350) return "1Y";
        if (days <= 1800) return "5Y";
        return "10Y";
    }

    /** BI kote grafik serisinden {@code date} ve öncesine en yakın kapanış (temiz, döviz). */
    private BigDecimal quoteAtOrBefore(String isin, String range, LocalDate date) {
        List<EurobondChartPoint> pts = chart(isin, range);
        if (pts == null || pts.isEmpty()) {
            return null;
        }
        BigDecimal best = null;
        LocalDate bestDay = null;
        for (EurobondChartPoint p : pts) {
            if (p == null || p.date() == null || p.close() == null) {
                continue;
            }
            LocalDate day;
            try {
                day = LocalDate.parse(p.date().substring(0, Math.min(10, p.date().length())));
            } catch (Exception e) {
                continue;
            }
            if (!day.isAfter(date) && (bestDay == null || day.isAfter(bestDay))) {
                bestDay = day;
                best = p.close();
            }
        }
        return best;
    }

    /** TCMB tarihsel kur serisinden {@code date} ve öncesine en yakın satış kuru (birim normalize). */
    private BigDecimal fxAtOrBefore(String currency, String range, LocalDate date) {
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        FxHistory hist = marketFxService.getFxHistory(currency, range);
        if (hist == null || hist.getPoints() == null || hist.getPoints().isEmpty()) {
            return null;
        }
        BigDecimal best = null;
        LocalDate bestDay = null;
        for (FxHistoryPoint p : hist.getPoints()) {
            if (p == null || p.getDate() == null || p.getClose() == null) {
                continue;
            }
            LocalDate day;
            try {
                day = LocalDate.parse(p.getDate().substring(0, Math.min(10, p.getDate().length())));
            } catch (Exception e) {
                continue;
            }
            if (!day.isAfter(date) && (bestDay == null || day.isAfter(bestDay))) {
                bestDay = day;
                best = p.getClose();
            }
        }
        return best;
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
        // Cache temizliği BEST-EFFORT: refreshFromXlsx zaten ISIN'leri güncelledi. Redis erişilemezse
        // (ör. geçici kesinti ya da Redis'siz test/CI ortamı) tüm tazelemeyi 502'ye düşürme — bayat
        // cache TTL ile zaten dolar. Hata yalnızca loglanır.
        try {
            Cache c = cacheManager.getCache(cache);
            if (c != null) {
                c.clear();
            }
        } catch (Exception e) {
            log.warn("Eurobond cache evict atlandı ({}): {}", cache, e.getMessage());
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
