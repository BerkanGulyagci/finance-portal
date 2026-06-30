package com.finance.portal.market.application.bond.evds;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.evds.model.BondClassifier;
import com.finance.portal.market.application.bond.evds.model.BondCurrency;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
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
 *   <li>{@code market.evds.bonds.list}          — 6 saat (warm-up scheduler 2 saatte bir @CachePut ile tazeler)
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

    /** Liste/detay (hızlı) veri LKG ömrü — kaynak çökerse en fazla bu kadar eski seri/değer servis edilir. */
    private static final Duration BOND_LIST_LKG_TTL = Duration.ofDays(3);
    /** Geçmiş (yavaş, ~değişmeyen) seri LKG ömrü. */
    private static final Duration BOND_HISTORY_LKG_TTL = Duration.ofDays(14);

    /**
     * "Özel tertip" / kapitalize eden kuponlu kıymetler: kupon ödenmeyip anaparaya eklenir, gösterge
     * değeri par'ın çok üstüne tırmanır (ör. 142, 352). İtfaları par-100 modeline uymaz (gerçek itfa
     * biriken tutardır) ve retail enstrüman değiller — evrenden çıkarılırlar. Eşik 130: normal kuponlu
     * kıymetler ~99–111 aralığında olduğundan onları etkilemez (ör. 49TDOZ, 73TOZ tipi ~2 kıymet).
     */
    private static final BigDecimal CAPITALIZING_INDICATOR_THRESHOLD = new BigDecimal("130");

    /**
     * Mantıklı kupon oranı üst sınırı (%). EVDS {@code .ORAN} serisi bazı likit olmayan uzun vadeli
     * kıymetlerde kupon oranı yerine <b>vadeye kalan gün sayısını</b> döndürüyor (gözlenen:
     * TRT211229F12 couponRate=1271=remainingDays, TRT250232F15=2067). Hiçbir DİBS kuponu %100'ü
     * aşmadığından, üstündeki "oran" sahte kabul edilip {@code null}'lanır (UI yanlış %1.271 yerine "—").
     */
    private static final BigDecimal MAX_PLAUSIBLE_COUPON_RATE = new BigDecimal("100");
    private static final Set<BondCategory> FIXED_COUPON_FAMILY = Set.of(
            BondCategory.FIXED_COUPON_BOND,
            BondCategory.PRINCIPAL_STRIP,
            BondCategory.COUPON_STRIP,
            BondCategory.TLREF_INDEXED_BOND);

    private final EvdsBondPort evdsBondPort;
    private final ExecutorService evdsBondFetchExecutor;
    private final LastKnownGoodCache lkg;
    private final TcmbDibsClassificationService dibsClassification;

    @Value("${evds.use-whitelist:false}")
    private boolean useWhitelist;

    @Value("${evds.bond-instruments:}")
    private List<String> whitelistInstruments;

    @Value("${evds.active-bonds-limit:0}")
    private int activeBondsLimit;

    @Value("${evds.lease-cert-data-group:bie_pyks}")
    private String leaseCertDataGroup;

    @Value("${evds.include-lease-certs:true}")
    private boolean includeLeaseCerts;

    public EvdsBondService(EvdsBondPort evdsBondPort,
                           @Qualifier("evdsBondFetchExecutor") ExecutorService evdsBondFetchExecutor,
                           LastKnownGoodCache lkg,
                           TcmbDibsClassificationService dibsClassification) {
        this.evdsBondPort = evdsBondPort;
        this.evdsBondFetchExecutor = evdsBondFetchExecutor;
        this.lkg = lkg;
        this.dibsClassification = dibsClassification;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Vadesi geçmemiş tüm aktif DİBS kıymetlerini döndürür (pagination yok).
     * Controller katmanında pagination/filter/sort uygulanır.
     * Cache TTL: 6 saat (warm-up scheduler 2 saatte bir {@link #refreshEvdsBondsAll()} ile tazeler).
     */
    @Cacheable(cacheNames = "market.evds.bonds.list", key = "'all'")
    public List<EvdsBondInstrument> getEvdsBondsAll() {
        return loadAllBonds();
    }

    /**
     * Tüm aktif tahvil/kira sertifikası listesini EVDS'ten yeniden çekip cache'i GÜNCELLER.
     * Warm-up scheduler çağırır — {@code @CachePut}: her zaman çalışır ve cache'i atomik değiştirir
     * (evict→reload boşluğu yok). Amaç: kullanıcı isteği 537+ çağrılık soğuk yola hiç düşmesin.
     */
    @CachePut(cacheNames = "market.evds.bonds.list", key = "'all'")
    public List<EvdsBondInstrument> refreshEvdsBondsAll() {
        log.info("[EvdsBondService] refreshEvdsBondsAll (warm-up) — bonds.list cache'i yenileniyor.");
        return loadAllBonds();
    }

    /**
     * Tüm EVDS DİBS cache'lerini boşaltır (list / detail / history / active-series).
     *
     * <p>Maturity parser fix (TR[BTD]DDMMYY code-based) gibi tek seferlik
     * deploy-sonrası temizlik için kullanılır — TTL'in (6h) dolmasını beklemeden
     * cache'in yeni verilerle dolmasını sağlar.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "market.evds.bonds.list",           allEntries = true),
            @CacheEvict(cacheNames = "market.evds.bonds.detail",         allEntries = true),
            @CacheEvict(cacheNames = "market.evds.bonds.history",        allEntries = true),
            @CacheEvict(cacheNames = "market.evds.bonds.active-series",  allEntries = true)
    })
    public void evictAllBondCaches() {
        log.info("[EvdsBondService] evictAllBondCaches — tüm DİBS cache'leri boşaltıldı.");
    }

    private List<EvdsBondInstrument> loadAllBonds() {
        log.info("[EvdsBondService] getEvdsBondsAll başlatıldı. useWhitelist={} includeLeaseCerts={}",
                useWhitelist, includeLeaseCerts);

        // 1) DİBS (bie_pydibs) — EVDS çökerse son başarılı seri listesini servis et.
        List<EvdsSeriesInfo> allSeries = new ArrayList<>(lkg.resilient(
                "evds.bond.series-list", BOND_LIST_LKG_TTL,
                new TypeReference<List<EvdsSeriesInfo>>() {}, evdsBondPort::fetchBondSeriesList));

        // 2) Kira Sertifikaları (bie_pyks) — opsiyonel, varsayılan açık
        if (includeLeaseCerts && leaseCertDataGroup != null && !leaseCertDataGroup.isBlank()) {
            try {
                List<EvdsSeriesInfo> kiraSeries = lkg.resilient(
                        "evds.bond.series-list:" + leaseCertDataGroup, BOND_LIST_LKG_TTL,
                        new TypeReference<List<EvdsSeriesInfo>>() {},
                        () -> evdsBondPort.fetchSeriesList(leaseCertDataGroup));
                log.info("[EvdsBondService] Kira Sertifikası serisi yüklendi: {} kayıt", kiraSeries.size());
                allSeries.addAll(kiraSeries);
            } catch (Exception e) {
                log.warn("[EvdsBondService] Kira Sertifikası seri listesi alınamadı ({}): {}",
                        leaseCertDataGroup, e.getMessage());
            }
        }

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
     *
     * <p>Kıymet EVDS'de yok ise {@code null} döner; Spring @Cacheable null değeri de
     * cache'ler (NullValue sentinel) — böylece Eurobond (XS / US prefix) veya vadesi
     * geçmiş DİBS sembolleri tekrar tekrar TCMB'ye gidip 20-30s stall etmez. (Portföy
     * liste yolu cold-path 80-150s → ~ms düşüş; çağıranlar zaten null veya exception
     * için graceful.)
     */
    @Cacheable(cacheNames = "market.evds.bonds.detail", key = "#instrumentCode")
    public EvdsBondInstrument getEvdsBondDetail(String instrumentCode) {
        log.info("[EvdsBondService] getEvdsBondDetail → instrumentCode={}", instrumentCode);

        List<EvdsSeriesInfo> allSeries = lkg.resilient(
                "evds.bond.series-list", BOND_LIST_LKG_TTL,
                new TypeReference<List<EvdsSeriesInfo>>() {}, evdsBondPort::fetchBondSeriesList);
        Map<String, EvdsSeriesInfo> seriesInfoMap = allSeries.stream()
                .filter(EvdsSeriesInfo::isValueSeries)
                .collect(Collectors.toMap(EvdsSeriesInfo::getSeriesCode, Function.identity(), (a, b) -> a));

        boolean hasCoupon = allSeries.stream()
                .filter(EvdsSeriesInfo::isCouponRateSeries)
                .anyMatch(s -> instrumentCode.equals(s.extractInstrumentCode()));

        EvdsSeriesInfo info = seriesInfoMap.get("TP." + instrumentCode);
        EvdsBondInstrument instrument = buildInstrument(instrumentCode, info, hasCoupon);

        if (instrument == null) {
            // Eskiden throw IllegalArgumentException → Spring @Cacheable exception cache'lemezdi,
            // her istek aynı yavaş EVDS lookup'unu tekrarlardı. Şimdi null döndürüyoruz; default
            // @Cacheable null'u NullValue sentinel ile cache'liyor → 1 saat TTL içinde tekrar EVDS
            // çağrısı yapılmaz. Çağıranlar (PortfolioHoldingsBuilder, toTransactionResponse, vd.)
            // null'u zaten "default parScale=100, par-unscaled değil" şeklinde graceful ele alıyor.
            log.info("[EvdsBondService] getEvdsBondDetail ← instrumentCode={} BULUNAMADI (null cache'lenecek)",
                    instrumentCode);
            return null;
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

        // Geçmiş seri ~değişmez → uzun LKG; EVDS çökerse son başarılı seriyi servis et.
        List<EvdsSeriesPoint> rawPoints = lkg.resilient(
                "evds.bond.history:" + instrumentCode + ":" + period.name(), BOND_HISTORY_LKG_TTL,
                new TypeReference<List<EvdsSeriesPoint>>() {},
                () -> evdsBondPort.fetchIndicatorValues(instrumentCode, startDate, endDate));

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

        List<EvdsSeriesInfo> allSeries = lkg.resilient(
                "evds.bond.series-list", BOND_LIST_LKG_TTL,
                new TypeReference<List<EvdsSeriesInfo>>() {}, evdsBondPort::fetchBondSeriesList);
        LocalDate today = LocalDate.now();

        long totalCount  = allSeries.size();
        long valueCount  = allSeries.stream().filter(EvdsSeriesInfo::isValueSeries).count();
        long couponCount = allSeries.stream().filter(EvdsSeriesInfo::isCouponRateSeries).count();

        List<EvdsSeriesInfo> active = allSeries.stream()
                .filter(EvdsSeriesInfo::isValueSeries)
                .filter(s -> {
                    // Precedence: SERIE_NAME parantezi → instrument kodu → END_DATE (son çare).
                    LocalDate maturity = resolveMaturityDate(s.extractInstrumentCode(), s);
                    // maturityDate >= today ise aktif
                    return maturity != null && !maturity.isBefore(today);
                })
                .sorted(Comparator.comparing(s -> {
                    LocalDate m = s.parseMaturityDateFromName();
                    if (m == null) m = EvdsSeriesInfo.parseMaturityDateFromCode(s.extractInstrumentCode());
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

        // Son gösterge-değeri penceresi (liste/detay) — EVDS çökerse son başarılı pencereyi servis et.
        List<EvdsSeriesPoint> valuePoints = lkg.resilient(
                "evds.bond.value:" + instrumentCode, BOND_LIST_LKG_TTL,
                new TypeReference<List<EvdsSeriesPoint>>() {},
                () -> evdsBondPort.fetchIndicatorValues(instrumentCode, startDate, today));

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

        // Finansal kategoriyi CBRT kodundan (yoksa ISIN suffix'inden) tespit et.
        // Data group (bie_pydibs vs bie_pyks) Kira Sertifikası ayrımı için kuvvetli sinyal.
        String cbrtCode = info != null ? info.parseCbrtCode() : null;
        String dataGroupCode = info != null ? info.getDatagroupCode() : null;
        // Otoriter kaynak: TCMB dibs1.txt bölüm üyeliği. Harita yoksa / ISIN bulunamazsa
        // (TCMB erişilemediyse) eski heuristik kod-deseni sınıflandırıcısına düş.
        BondCategory category = dibsClassification.categoryOf(instrumentCode);
        if (category == null) {
            category = BondClassifier.classify(instrumentCode, cbrtCode, dataGroupCode);
        }
        // Kapitalize eden özel-tertip kuponlu kıymetleri evrenden çıkar (gösterge ≫ par → kupon
        // anaparaya kapitalize oluyor; par-100 itfası yanlış, retail değil). Veri-temelli: gelecekte
        // böyle bir kıymet çıkarsa otomatik elenir, normal kuponlular (~99–111) etkilenmez.
        if (FIXED_COUPON_FAMILY.contains(category)
                && indicatorValue != null
                && indicatorValue.compareTo(CAPITALIZING_INDICATOR_THRESHOLD) > 0) {
            log.debug("[EvdsBondService] {} kapitalize özel-tertip (kategori={} gösterge={}) — evrenden çıkarıldı.",
                    instrumentCode, category, indicatorValue);
            return null;
        }
        BondCurrency currency = BondClassifier.currencyFor(category);

        // Kupon oranı — sadece kupon ÖDEYEN kategoriler için çek. Kuponsuz bondların EVDS'teki
        // .ORAN serisi yanıltıcı (örn. vadeye kalan gün sayısını içeriyor) — couponRate=null bırak.
        boolean isZeroCoupon = category == BondCategory.ZERO_COUPON_BILL
                || category == BondCategory.ZERO_COUPON_BOND
                || category == BondCategory.PRINCIPAL_STRIP
                || category == BondCategory.INFLATION_PRINCIPAL_STRIP;
        BigDecimal couponRate = (hasCoupon && !isZeroCoupon)
                ? fetchLatestCouponRate(instrumentCode, startDate, today)
                : null;

        EvdsBondInstrument instrument = new EvdsBondInstrument();
        instrument.setInstrumentCode(instrumentCode);
        // Kira sertifikası (sukuk) ise type'ı "Kira Sertifikası" yap (Tier 3 etiketi).
        instrument.setType(category != null && category.isLeaseCertificate()
                ? "Kira Sertifikası"
                : resolveType(instrumentCode));
        instrument.setCategory(category);
        instrument.setCurrency(currency);
        instrument.setCbrtCode(cbrtCode);
        // SERIE_NAME'den parse edilemezse EVDS START_DATE / END_DATE fallback (remainingDays ile uyumlu)
        LocalDate issueD = info != null ? info.parseIssueDateFromName() : null;
        if (issueD == null && info != null) issueD = info.getStartDate();
        instrument.setIssueDate(issueD);
        // Vade tarihi precedence: SERIE_NAME parantezi → instrument kodu (DD-MM-YY) → END_DATE (son veri tarihi, son çare).
        // END_DATE bir veri-availability işaretçisidir, gerçek vade DEĞİL — 11 sembolde SERIE_NAME format farklı,
        // dolayısıyla code-based parse devreye girer.
        LocalDate maturityD = resolveMaturityDate(instrumentCode, info);
        instrument.setMaturityDate(maturityD);
        int remainingDays = calculateRemainingDays(instrumentCode, info, today);
        instrument.setRemainingDays(remainingDays);
        instrument.setIndicatorValue(indicatorValue);
        instrument.setPreviousValue(previousValue);
        instrument.setDailyChange(dailyChange);
        instrument.setDailyChangePercent(dailyChangePercent);
        // EVDS .ORAN serisi bazı likit olmayan uzun vadeli kıymetlerde kupon oranı yerine VADEYE
        // KALAN GÜN sayısını döndürüyor (TRT211229F12 couponRate=1271=remainingDays; TRT250232F15=2067).
        // Hiçbir DİBS kuponu %100'ü aşmaz → 100'ün üstündeki değer sahte (gün sızıntısı) kabul edilip
        // null'lanır; böylece ileride benzer kıymet çıkarsa otomatik elenir (hardcode liste değil).
        if (couponRate != null && couponRate.compareTo(MAX_PLAUSIBLE_COUPON_RATE) > 0) {
            log.debug("[EvdsBondService] {} kupon oranı mantıksız (={}, remainingDays={}) — "
                    + "EVDS .ORAN gün-sızıntısı, null'landı", instrumentCode, couponRate, remainingDays);
            couponRate = null;
        }
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

    private int calculateRemainingDays(String instrumentCode, EvdsSeriesInfo info, LocalDate today) {
        LocalDate maturityDate = resolveMaturityDate(instrumentCode, info);
        if (maturityDate == null) return 0;
        long days = today.until(maturityDate, java.time.temporal.ChronoUnit.DAYS);
        return (int) Math.max(0, days);
    }

    /**
     * Vade tarihi çözümleme precedence:
     *  1) SERIE_NAME parantezi ({@link EvdsSeriesInfo#parseMaturityDateFromName()})
     *  2) Instrument kodu — TR[BTD]DDMMYY... format ({@link EvdsSeriesInfo#parseMaturityDateFromCode(String)})
     *  3) EVDS END_DATE (son veri-noktası tarihi) — SON ÇARE, gerçek vade DEĞİL,
     *     veri-availability işaretçisi. Düştüğümüzde warn loglarız.
     */
    private LocalDate resolveMaturityDate(String instrumentCode, EvdsSeriesInfo info) {
        if (info != null) {
            LocalDate fromName = info.parseMaturityDateFromName();
            if (fromName != null) return fromName;
        }
        LocalDate fromCode = EvdsSeriesInfo.parseMaturityDateFromCode(instrumentCode);
        if (fromCode != null) return fromCode;
        LocalDate fromEndDate = info != null ? info.getEndDate() : null;
        if (fromEndDate != null) {
            log.warn("[EvdsBondService] Instrument {} maturity falling back to END_DATE " +
                    "(data-availability marker) — investigate SERIE_NAME format", instrumentCode);
        }
        return fromEndDate;
    }

    private BigDecimal fetchLatestCouponRate(String instrumentCode,
                                              LocalDate startDate, LocalDate endDate) {
        try {
            // Kupon oranı serisi — EVDS çökerse son başarılı oran penceresini servis et.
            List<EvdsSeriesPoint> couponPoints = lkg.resilient(
                    "evds.bond.coupon:" + instrumentCode, BOND_LIST_LKG_TTL,
                    new TypeReference<List<EvdsSeriesPoint>>() {},
                    () -> evdsBondPort.fetchCouponRates(instrumentCode, startDate, endDate));
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
                    // Precedence: SERIE_NAME parantezi → instrument kodu → END_DATE (son çare).
                    LocalDate maturity = resolveMaturityDate(s.extractInstrumentCode(), s);
                    return maturity != null && !maturity.isBefore(today);
                })
                .sorted(Comparator.comparing(s -> {
                    LocalDate m = s.parseMaturityDateFromName();
                    if (m == null) m = EvdsSeriesInfo.parseMaturityDateFromCode(s.extractInstrumentCode());
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
