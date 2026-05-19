package com.finance.portal.market.application.bond.evds;

import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * EVDS DİBS verilerini normalize eden uygulama servisi.
 *
 * <p>İki mod:
 * <ul>
 *   <li><b>Whitelist modu</b> ({@code evds.use-whitelist=true}):
 *       {@code evds.bond-instruments} listesindeki kodlar kullanılır.
 *   <li><b>Aktif seri modu</b> ({@code evds.use-whitelist=false}):
 *       EVDS'den vadesi geçmemiş tüm aktif Değer serileri otomatik bulunur.
 * </ul>
 *
 * <p>Cache isimleri:
 * <ul>
 *   <li>{@code market.evds.bonds.active-series} — 12 saat (aktif seri listesi)
 *   <li>{@code market.evds.bonds.list}          — 1 saat
 *   <li>{@code market.evds.bonds.detail}        — 1 saat
 *   <li>{@code market.evds.bonds.history}       — 2 saat
 * </ul>
 */
@Service
public class EvdsBondService {

    private static final Logger log = LoggerFactory.getLogger(EvdsBondService.class);

    private static final String SOURCE = "TCMB EVDS";

    /** Gösterge değeri için kaç günlük pencere çekileceği */
    private static final int RECENT_DAYS_WINDOW = 7;

    private static final DateTimeFormatter DATE_TEXT_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final EvdsBondPort evdsBondPort;
    private final ExecutorService evdsBondFetchExecutor;

    @Value("${evds.use-whitelist:false}")
    private boolean useWhitelist;

    @Value("${evds.bond-instruments:}")
    private List<String> whitelistInstruments;

    @Value("${evds.active-bonds-limit:300}")
    private int activeBondsLimit;

    public EvdsBondService(EvdsBondPort evdsBondPort,
                           @Qualifier("evdsBondFetchExecutor") ExecutorService evdsBondFetchExecutor) {
        this.evdsBondPort = evdsBondPort;
        this.evdsBondFetchExecutor = evdsBondFetchExecutor;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Vadesi geçmemiş tüm aktif DİBS kıymetlerini döndürür (pagination yok).
     * Controller katmanında pagination/filter/sort uygulanır.
     * Cache TTL: 1 saat.
     */
    @Cacheable(cacheNames = "market.evds.bonds.list", key = "'all'")
    public List<EvdsBondInstrument> getEvdsBondsAll() {
        log.info("[EvdsBondService] getEvdsBondsAll başlatıldı. useWhitelist={}", useWhitelist);

        List<EvdsSeriesInfo> allSeries = evdsBondPort.fetchBondSeriesList();

        Map<String, EvdsSeriesInfo> seriesInfoMap = allSeries.stream()
                .filter(EvdsSeriesInfo::isValueSeries)
                .collect(Collectors.toMap(
                        EvdsSeriesInfo::getSeriesCode,
                        Function.identity(),
                        (a, b) -> a
                ));

        Set<String> couponSeriesCodes = allSeries.stream()
                .filter(EvdsSeriesInfo::isCouponRateSeries)
                .map(EvdsSeriesInfo::extractInstrumentCode)
                .collect(Collectors.toSet());

        // Limit uygulanır — evds.active-bonds-limit (0 = limitsiz)
        List<String> instrumentCodes = resolveInstrumentCodes(seriesInfoMap);
        if (instrumentCodes.isEmpty()) {
            log.warn("[EvdsBondService] İşlenecek aktif instrument kodu bulunamadı.");
            return List.of();
        }

        log.info("[EvdsBondService] {} aktif kıymet işlenecek (paralel EVDS).", instrumentCodes.size());

        List<CompletableFuture<EvdsBondInstrument>> futures = new ArrayList<>(instrumentCodes.size());
        for (String code : instrumentCodes) {
            EvdsSeriesInfo info = seriesInfoMap.get("TP." + code);
            boolean hasCoupon = couponSeriesCodes.contains(code);
            futures.add(CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return buildInstrument(code, info, hasCoupon);
                        } catch (Exception e) {
                            log.warn("[EvdsBondService] {} işlenirken hata: {}", code, e.getMessage());
                            return null;
                        }
                    },
                    evdsBondFetchExecutor));
        }

        List<EvdsBondInstrument> result = new ArrayList<>();
        for (CompletableFuture<EvdsBondInstrument> future : futures) {
            EvdsBondInstrument instrument = future.join();
            if (instrument != null) {
                result.add(instrument);
            }
        }

        log.info("[EvdsBondService] getEvdsBondsAll tamamlandı. {} kıymet.", result.size());
        return result;
    }

    /**
     * @deprecated Eski API — getEvdsBondsAll() kullanın.
     * Geriye uyumluluk için bırakıldı.
     */
    @Deprecated
    public List<EvdsBondInstrument> getEvdsBonds() {
        return getEvdsBondsAll();
    }

    /**
     * Tekil kıymetin EVDS detayını döndürür.
     */
    @Cacheable(cacheNames = "market.evds.bonds.detail", key = "#instrumentCode")
    public EvdsBondInstrument getEvdsBondDetail(String instrumentCode) {
        log.info("[EvdsBondService] getEvdsBondDetail → instrumentCode={}", instrumentCode);

        List<EvdsSeriesInfo> allSeries = evdsBondPort.fetchBondSeriesList();
        Map<String, EvdsSeriesInfo> seriesInfoMap = allSeries.stream()
                .filter(EvdsSeriesInfo::isValueSeries)
                .collect(Collectors.toMap(EvdsSeriesInfo::getSeriesCode, Function.identity(), (a, b) -> a));

        boolean hasCoupon = allSeries.stream()
                .filter(EvdsSeriesInfo::isCouponRateSeries)
                .anyMatch(s -> instrumentCode.equals(s.extractInstrumentCode()));

        EvdsSeriesInfo info = seriesInfoMap.get("TP." + instrumentCode);
        EvdsBondInstrument instrument = buildInstrument(instrumentCode, info, hasCoupon);

        if (instrument == null) {
            throw new IllegalArgumentException(
                    "EVDS'de kıymet bulunamadı veya veri yok: " + instrumentCode);
        }

        log.info("[EvdsBondService] getEvdsBondDetail ← instrumentCode={} indicatorValue={}",
                instrumentCode, instrument.getIndicatorValue());
        return instrument;
    }

    /**
     * Kıymetin tarihsel EVDS gösterge değerlerini döndürür.
     */
    @Cacheable(cacheNames = "market.evds.bonds.history", key = "#instrumentCode + '_' + #period.name()")
    public List<EvdsBondHistoryPoint> getEvdsBondHistory(String instrumentCode, BondPeriod period) {
        log.info("[EvdsBondService] getEvdsBondHistory → instrumentCode={} period={}", instrumentCode, period);

        LocalDate endDate   = LocalDate.now();
        LocalDate startDate = endDate.minusDays(period.getDays());

        List<EvdsSeriesPoint> rawPoints = evdsBondPort.fetchIndicatorValues(instrumentCode, startDate, endDate);

        List<EvdsBondHistoryPoint> history = rawPoints.stream()
                .map(p -> new EvdsBondHistoryPoint(
                        p.getDate(),
                        p.getDate().format(DATE_TEXT_FMT),
                        instrumentCode,
                        p.getValue()))
                .collect(Collectors.toList());

        log.info("[EvdsBondService] getEvdsBondHistory ← instrumentCode={} period={} points={}",
                instrumentCode, period, history.size());
        return history;
    }

    /**
     * Vadesi geçmemiş aktif Değer serilerini döndürür.
     * 12 saat cache'lenir — seri listesi sık değişmez.
     */
    @Cacheable(cacheNames = "market.evds.bonds.active-series", key = "'active'")
    public List<EvdsSeriesInfo> fetchActiveBondSeries() {
        log.info("[EvdsBondService] fetchActiveBondSeries başlatıldı.");

        List<EvdsSeriesInfo> allSeries = evdsBondPort.fetchBondSeriesList();
        LocalDate today = LocalDate.now();

        long totalCount  = allSeries.size();
        long valueCount  = allSeries.stream().filter(EvdsSeriesInfo::isValueSeries).count();
        long couponCount = allSeries.stream().filter(EvdsSeriesInfo::isCouponRateSeries).count();

        List<EvdsSeriesInfo> active = allSeries.stream()
                .filter(EvdsSeriesInfo::isValueSeries)
                .filter(s -> {
                    LocalDate maturity = s.parseMaturityDateFromName();
                    if (maturity == null) {
                        // SERIE_NAME'den parse edilemezse END_DATE'e bak
                        maturity = s.getEndDate();
                    }
                    // maturityDate >= today ise aktif
                    return maturity != null && !maturity.isBefore(today);
                })
                .sorted(Comparator.comparing(s -> {
                    LocalDate m = s.parseMaturityDateFromName();
                    return m != null ? m : LocalDate.MAX;
                }))
                .collect(Collectors.toList());

        long expiredCount = valueCount - active.size();

        log.info("[EvdsBondService] fetchActiveBondSeries raporu: " +
                        "toplam={} değer={} kupon={} vadesiGeçmiş={} aktif={}",
                totalCount, valueCount, couponCount, expiredCount, active.size());

        return active;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — instrument build
    // ─────────────────────────────────────────────────────────────────────────

    private EvdsBondInstrument buildInstrument(String instrumentCode,
                                                EvdsSeriesInfo info,
                                                boolean hasCoupon) {
        LocalDate today     = LocalDate.now();
        LocalDate startDate = today.minusDays(RECENT_DAYS_WINDOW);

        List<EvdsSeriesPoint> valuePoints =
                evdsBondPort.fetchIndicatorValues(instrumentCode, startDate, today);

        if (valuePoints.isEmpty()) {
            log.debug("[EvdsBondService] {} için değer verisi yok, atlandı.", instrumentCode);
            return null;
        }

        BigDecimal indicatorValue  = valuePoints.get(valuePoints.size() - 1).getValue();
        BigDecimal previousValue   = valuePoints.size() >= 2
                ? valuePoints.get(valuePoints.size() - 2).getValue()
                : null;
        BigDecimal dailyChange        = calculateDailyChange(indicatorValue, previousValue);
        BigDecimal dailyChangePercent = calculateDailyChangePercent(indicatorValue, previousValue);

        // Kupon oranı — sadece kupon serisi varsa çek
        BigDecimal couponRate = hasCoupon
                ? fetchLatestCouponRate(instrumentCode, startDate, today)
                : null;

        EvdsBondInstrument instrument = new EvdsBondInstrument();
        instrument.setInstrumentCode(instrumentCode);
        instrument.setType(resolveType(instrumentCode));
        instrument.setIssueDate(info != null ? info.parseIssueDateFromName() : null);
        instrument.setMaturityDate(info != null ? info.parseMaturityDateFromName() : null);
        instrument.setRemainingDays(calculateRemainingDays(info, today));
        instrument.setIndicatorValue(indicatorValue);
        instrument.setPreviousValue(previousValue);
        instrument.setDailyChange(dailyChange);
        instrument.setDailyChangePercent(dailyChangePercent);
        instrument.setCouponRate(couponRate);
        instrument.setSource(SOURCE);
        instrument.setLastUpdated(valuePoints.get(valuePoints.size() - 1).getDate());

        return instrument;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — hesaplamalar
    // ─────────────────────────────────────────────────────────────────────────

    BigDecimal calculateDailyChange(BigDecimal indicatorValue, BigDecimal previousValue) {
        if (indicatorValue == null || previousValue == null) return null;
        return indicatorValue.subtract(previousValue);
    }

    BigDecimal calculateDailyChangePercent(BigDecimal indicatorValue, BigDecimal previousValue) {
        if (indicatorValue == null || previousValue == null) return null;
        if (previousValue.compareTo(BigDecimal.ZERO) == 0) return null;
        return indicatorValue.subtract(previousValue)
                .divide(previousValue, MathContext.DECIMAL64)
                .multiply(BigDecimal.valueOf(100))
                .setScale(6, RoundingMode.HALF_UP);
    }

    String resolveType(String instrumentCode) {
        if (instrumentCode == null) return "DİBS";
        if (instrumentCode.startsWith("TRB")) return "Hazine Bonosu";
        if (instrumentCode.startsWith("TRT")) return "Devlet Tahvili";
        return "DİBS";
    }

    private int calculateRemainingDays(EvdsSeriesInfo info, LocalDate today) {
        if (info == null) return 0;
        LocalDate maturityDate = info.parseMaturityDateFromName();
        if (maturityDate == null) maturityDate = info.getEndDate();
        if (maturityDate == null) return 0;
        long days = today.until(maturityDate, java.time.temporal.ChronoUnit.DAYS);
        return (int) Math.max(0, days);
    }

    private BigDecimal fetchLatestCouponRate(String instrumentCode,
                                              LocalDate startDate, LocalDate endDate) {
        try {
            List<EvdsSeriesPoint> couponPoints =
                    evdsBondPort.fetchCouponRates(instrumentCode, startDate, endDate);
            if (couponPoints.isEmpty()) return null;
            return couponPoints.get(couponPoints.size() - 1).getValue();
        } catch (Exception e) {
            log.debug("[EvdsBondService] {} kupon oranı çekilemedi: {}", instrumentCode, e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal — instrument kodu listesi belirleme
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * use-whitelist=true → whitelist döner.
     * use-whitelist=false → EVDS'den vadesi geçmemiş aktif Değer serileri döner.
     * active-bonds-limit > 0 ise sonuç limit ile kırpılır.
     */
    private List<String> resolveInstrumentCodes(Map<String, EvdsSeriesInfo> seriesInfoMap) {
        if (useWhitelist) {
            if (whitelistInstruments == null || whitelistInstruments.isEmpty()) {
                log.warn("[EvdsBondService] use-whitelist=true ama bond-instruments boş.");
                return List.of();
            }
            log.info("[EvdsBondService] Whitelist modu: {} kıymet.", whitelistInstruments.size());
            return applyActiveBondsLimit(new ArrayList<>(whitelistInstruments));
        }

        // Aktif seri modu — seriesInfoMap zaten Değer serilerini içeriyor
        LocalDate today = LocalDate.now();

        List<String> activeCodes = seriesInfoMap.values().stream()
                .filter(s -> {
                    LocalDate maturity = s.parseMaturityDateFromName();
                    if (maturity == null) maturity = s.getEndDate();
                    return maturity != null && !maturity.isBefore(today);
                })
                .sorted(Comparator.comparing(s -> {
                    LocalDate m = s.parseMaturityDateFromName();
                    return m != null ? m : LocalDate.MAX;
                }))
                .map(EvdsSeriesInfo::extractInstrumentCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toList());

        long totalValue   = seriesInfoMap.size();
        long expiredCount = totalValue - activeCodes.size();
        log.info("[EvdsBondService] Aktif seri modu: toplam_değer={} vadesiGeçmiş={} aktif={}",
                totalValue, expiredCount, activeCodes.size());

        return applyActiveBondsLimit(activeCodes);
    }

    /**
     * {@code evds.active-bonds-limit} &gt; 0 ise listeyi baştan kırpar (sıra korunur).
     */
    private List<String> applyActiveBondsLimit(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return codes == null ? List.of() : codes;
        }
        if (activeBondsLimit <= 0 || codes.size() <= activeBondsLimit) {
            return codes;
        }
        log.info("[EvdsBondService] active-bonds-limit={} uygulanıyor: {} → {} kıymet",
                activeBondsLimit, codes.size(), activeBondsLimit);
        return new ArrayList<>(codes.subList(0, activeBondsLimit));
    }
}
