package com.finance.portal.market.application.bond.evds;

import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.evds.model.BondCurrency;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvdsBondServiceTest {

    @Mock
    private EvdsBondPort evdsBondPort;

    /** Same-thread executor so CompletableFuture.supplyAsync runs synchronously/deterministically. */
    private ExecutorService executor;

    private EvdsBondService service;

    @BeforeEach
    void setUp() {
        executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        // LKG pass-through: resilient(...) sadece supplier'ı çağırır (port verify sayıları korunur).
        LastKnownGoodCache lkg = mock(LastKnownGoodCache.class);
        lenient().when(lkg.resilient(anyString(), any(Duration.class), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
        // TCMB otoriter harita boş (categoryOf → null) → heuristik BondClassifier'a düşülür,
        // böylece bu testin mevcut kategori beklentileri değişmeden kalır.
        TcmbDibsClassificationService dibsClassification = mock(TcmbDibsClassificationService.class);
        service = new EvdsBondService(evdsBondPort, executor, lkg, dibsClassification);
        // default @Value defaults
        ReflectionTestUtils.setField(service, "useWhitelist", false);
        ReflectionTestUtils.setField(service, "whitelistInstruments", List.of());
        ReflectionTestUtils.setField(service, "activeBondsLimit", 0);
        ReflectionTestUtils.setField(service, "leaseCertDataGroup", "bie_pyks");
        ReflectionTestUtils.setField(service, "includeLeaseCerts", true);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** A value series whose maturity (from SERIE_NAME) is in the future. */
    private EvdsSeriesInfo valueSeries(String instrumentCode, LocalDate issue, LocalDate maturity) {
        String name = instrumentCode + " ( " + fmt(issue) + " " + fmt(maturity) + " ) Değer (121T2)";
        return new EvdsSeriesInfo("TP." + instrumentCode, "bie_pydibs", name, null, "GÜNLÜK",
                issue, maturity);
    }

    /** A value series with NO parseable name dates — forces code/END_DATE precedence. */
    private EvdsSeriesInfo valueSeriesNoNameDates(String instrumentCode, LocalDate startDate, LocalDate endDate) {
        String name = instrumentCode + " Değer";
        return new EvdsSeriesInfo("TP." + instrumentCode, "bie_pydibs", name, null, "GÜNLÜK",
                startDate, endDate);
    }

    private EvdsSeriesInfo couponSeries(String instrumentCode) {
        return new EvdsSeriesInfo("TP." + instrumentCode + ".ORAN", "bie_pydibs",
                instrumentCode + " Kupon", null, "GÜNLÜK", null, null);
    }

    private static String fmt(LocalDate d) {
        return d.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private EvdsSeriesPoint pt(LocalDate d, String v) {
        return new EvdsSeriesPoint(d, new BigDecimal(v));
    }

    // ── getEvdsBondsAll (active-series mode) ──────────────────────────────────

    @Test
    void getEvdsBondsAll_buildsInstruments_fromActiveValueSeries() {
        LocalDate maturity = LocalDate.now().plusYears(2);
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17", LocalDate.now().minusYears(1), maturity);

        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(2), "99.00"),
                        pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        EvdsBondInstrument bond = result.get(0);
        assertThat(bond.getInstrumentCode()).isEqualTo("TRT240227T17");
        assertThat(bond.getIndicatorValue()).isEqualByComparingTo("100.00");
        assertThat(bond.getPreviousValue()).isEqualByComparingTo("99.00");
        assertThat(bond.getDailyChange()).isEqualByComparingTo("1.00");
        assertThat(bond.getSource()).isEqualTo("TCMB EVDS");
        assertThat(bond.getMaturityDate()).isEqualTo(maturity);
        assertThat(bond.getCategory()).isNotNull();
    }

    @Test
    void getEvdsBondsAll_skipsExpiredSeries() {
        EvdsSeriesInfo expired = valueSeries("TRT010120T17",
                LocalDate.now().minusYears(5), LocalDate.now().minusDays(1));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(expired));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).isEmpty();
        // expired filtered before indicator fetch
        verify(evdsBondPort, never()).fetchIndicatorValues(anyString(), any(), any());
    }

    @Test
    void getEvdsBondsAll_skipsInstrument_whenNoIndicatorData() {
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any())).thenReturn(List.of());

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).isEmpty();
    }

    @Test
    void getEvdsBondsAll_emptySeries_returnsEmptyList() {
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of());
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());

        assertThat(service.getEvdsBondsAll()).isEmpty();
    }

    @Test
    void getEvdsBondsAll_leaseCertFetchThrows_isToleratedAndDibsStillProcessed() {
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenThrow(new RuntimeException("pyks down"));
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
    }

    @Test
    void getEvdsBondsAll_includeLeaseCertsFalse_doesNotFetchPyks() {
        ReflectionTestUtils.setField(service, "includeLeaseCerts", false);
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        service.getEvdsBondsAll();

        verify(evdsBondPort, never()).fetchSeriesList(anyString());
    }

    @Test
    void getEvdsBondsAll_singlePoint_hasNoPreviousValueOrChange() {
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPreviousValue()).isNull();
        assertThat(result.get(0).getDailyChange()).isNull();
        assertThat(result.get(0).getDailyChangePercent()).isNull();
    }

    // ── Whitelist mode ───────────────────────────────────────────────────────

    @Test
    void getEvdsBondsAll_whitelistMode_usesConfiguredCodes() {
        ReflectionTestUtils.setField(service, "useWhitelist", true);
        ReflectionTestUtils.setField(service, "whitelistInstruments", List.of("TRT240227T17"));

        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(
                valueSeries("TRT240227T17", LocalDate.now().minusYears(1), LocalDate.now().plusYears(2))));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).extracting(EvdsBondInstrument::getInstrumentCode)
                .containsExactly("TRT240227T17");
    }

    @Test
    void getEvdsBondsAll_whitelistMode_emptyWhitelist_returnsEmpty() {
        ReflectionTestUtils.setField(service, "useWhitelist", true);
        ReflectionTestUtils.setField(service, "whitelistInstruments", List.of());
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of());
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());

        assertThat(service.getEvdsBondsAll()).isEmpty();
        verify(evdsBondPort, never()).fetchIndicatorValues(anyString(), any(), any());
    }

    @Test
    void getEvdsBondsAll_activeBondsLimit_trimsList() {
        ReflectionTestUtils.setField(service, "activeBondsLimit", 1);
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        EvdsSeriesInfo s2 = valueSeries("TRT250228T18",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1, s2));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        // only the first (earliest maturity) should be fetched
        when(evdsBondPort.fetchIndicatorValues(anyString(), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        // earliest maturity first → TRT240227T17
        assertThat(result.get(0).getInstrumentCode()).isEqualTo("TRT240227T17");
    }

    // ── getEvdsBonds (deprecated alias) ───────────────────────────────────────

    @Test
    void getEvdsBonds_delegatesToGetEvdsBondsAll() {
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of());
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());

        assertThat(service.getEvdsBonds()).isEmpty();
    }

    // ── getEvdsBondDetail ─────────────────────────────────────────────────────

    @Test
    void getEvdsBondDetail_returnsInstrument_whenPresent() {
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(2), "98.00"),
                        pt(LocalDate.now().minusDays(1), "100.00")));

        EvdsBondInstrument bond = service.getEvdsBondDetail("TRT240227T17");

        assertThat(bond).isNotNull();
        assertThat(bond.getInstrumentCode()).isEqualTo("TRT240227T17");
        assertThat(bond.getIndicatorValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void getEvdsBondDetail_returnsNull_whenInstrumentMissingFromSeries() {
        // series list has a different instrument → info null → no indicator data → null
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(
                valueSeries("TRT240227T17", LocalDate.now().minusYears(1), LocalDate.now().plusYears(2))));
        // Eurobond / unknown code → no indicator data
        when(evdsBondPort.fetchIndicatorValues(eq("XS1234567890"), any(), any())).thenReturn(List.of());

        EvdsBondInstrument bond = service.getEvdsBondDetail("XS1234567890");

        assertThat(bond).isNull();
    }

    @Test
    void getEvdsBondDetail_returnsNull_whenNoIndicatorData() {
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(
                valueSeries("TRT240227T17", LocalDate.now().minusYears(1), LocalDate.now().plusYears(2))));
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any())).thenReturn(List.of());

        EvdsBondInstrument bond = service.getEvdsBondDetail("TRT240227T17");

        assertThat(bond).isNull();
    }

    // ── getEvdsBondHistory ────────────────────────────────────────────────────

    @Test
    void getEvdsBondHistory_mapsPoints_withFormattedDate() {
        LocalDate d1 = LocalDate.of(2026, 1, 2);
        LocalDate d2 = LocalDate.of(2026, 1, 3);
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(d1, "99.5"), pt(d2, "100.5")));

        List<EvdsBondHistoryPoint> history = service.getEvdsBondHistory("TRT240227T17", BondPeriod.ONE_MONTH);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getDate()).isEqualTo(d1);
        assertThat(history.get(0).getDateText()).isEqualTo("02-01-2026");
        assertThat(history.get(0).getInstrumentCode()).isEqualTo("TRT240227T17");
        assertThat(history.get(0).getIndicatorValue()).isEqualByComparingTo("99.5");
        assertThat(history.get(1).getDateText()).isEqualTo("03-01-2026");
    }

    @Test
    void getEvdsBondHistory_emptyUpstream_returnsEmpty() {
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any())).thenReturn(List.of());

        List<EvdsBondHistoryPoint> history = service.getEvdsBondHistory("TRT240227T17", BondPeriod.ONE_YEAR);

        assertThat(history).isEmpty();
    }

    @Test
    void getEvdsBondHistory_usesPeriodDays_forStartDate() {
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any())).thenReturn(List.of());

        service.getEvdsBondHistory("TRT240227T17", BondPeriod.FIVE_YEARS);

        org.mockito.ArgumentCaptor<LocalDate> startCap = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        org.mockito.ArgumentCaptor<LocalDate> endCap = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(evdsBondPort).fetchIndicatorValues(eq("TRT240227T17"), startCap.capture(), endCap.capture());
        long days = startCap.getValue().until(endCap.getValue(), java.time.temporal.ChronoUnit.DAYS);
        assertThat(days).isEqualTo(BondPeriod.FIVE_YEARS.getDays());
    }

    // ── fetchActiveBondSeries ─────────────────────────────────────────────────

    @Test
    void fetchActiveBondSeries_returnsActiveSortedByMaturity_excludesCouponAndExpired() {
        EvdsSeriesInfo later = valueSeries("TRT250228T18",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(3));
        EvdsSeriesInfo earlier = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        EvdsSeriesInfo expired = valueSeries("TRT010120T17",
                LocalDate.now().minusYears(5), LocalDate.now().minusDays(5));
        EvdsSeriesInfo coupon = couponSeries("TRT240227T17"); // not a value series → excluded

        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(later, earlier, expired, coupon));

        List<EvdsSeriesInfo> active = service.fetchActiveBondSeries();

        // expired + coupon excluded; sorted ascending by maturity
        assertThat(active).extracting(EvdsSeriesInfo::extractInstrumentCode)
                .containsExactly("TRT240227T17", "TRT250228T18");
    }

    @Test
    void fetchActiveBondSeries_empty_returnsEmpty() {
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of());
        assertThat(service.fetchActiveBondSeries()).isEmpty();
    }

    // ── maturity resolution precedence ────────────────────────────────────────

    @Test
    void maturityResolution_codeBasedFallback_whenNameHasNoDates() {
        // SERIE_NAME without parseable dates; code TRT240227T17 → 2027-02-24 (future)
        EvdsSeriesInfo info = valueSeriesNoNameDates("TRT240227T17",
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1) /* END_DATE earlier, ignored */);
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(info));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        // code-based maturity = 2027-02-24, not the END_DATE (2025-01-01)
        assertThat(result.get(0).getMaturityDate()).isEqualTo(LocalDate.of(2027, 2, 24));
    }

    @Test
    void maturityResolution_endDateFallback_whenNoNameNoCode() {
        // Non-parseable instrument code (XS...) and no name dates → END_DATE used.
        LocalDate endDate = LocalDate.now().plusYears(1);
        EvdsSeriesInfo info = valueSeriesNoNameDates("XS1234567890",
                LocalDate.now().minusYears(1), endDate);
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(info));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("XS1234567890"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMaturityDate()).isEqualTo(endDate);
    }

    @Test
    void maturityResolution_namePrecedesCode() {
        // Name says future maturity 2030; code would say 2027. Name should win.
        LocalDate nameMaturity = LocalDate.of(2030, 6, 15);
        EvdsSeriesInfo info = valueSeries("TRT240227T17",
                LocalDate.of(2024, 1, 1), nameMaturity);
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(info));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result.get(0).getMaturityDate()).isEqualTo(nameMaturity);
    }

    // ── coupon rate ───────────────────────────────────────────────────────────

    @Test
    void couponRate_fetchedForCouponPayingCategory() {
        // TRT...T with 121T2 CBRT code → FIXED_COUPON_BOND (pays coupon, not zero-coupon)
        EvdsSeriesInfo value = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        EvdsSeriesInfo coupon = couponSeries("TRT240227T17");
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(value, coupon));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));
        when(evdsBondPort.fetchCouponRates(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "8.50")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouponRate()).isEqualByComparingTo("8.50");
    }

    @Test
    void couponRate_couponFetchThrows_isToleratedAsNull() {
        EvdsSeriesInfo value = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        EvdsSeriesInfo coupon = couponSeries("TRT240227T17");
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(value, coupon));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));
        when(evdsBondPort.fetchCouponRates(eq("TRT240227T17"), any(), any()))
                .thenThrow(new RuntimeException("coupon down"));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouponRate()).isNull();
    }

    @Test
    void couponRate_notFetched_whenNoCouponSeries() {
        EvdsSeriesInfo value = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(value));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouponRate()).isNull();
        verify(evdsBondPort, never()).fetchCouponRates(anyString(), any(), any());
    }

    // ── helper calculations (package-private) ─────────────────────────────────

    @Test
    void calculateDailyChange_handlesNulls() {
        assertThat(service.calculateDailyChange(null, BigDecimal.ONE)).isNull();
        assertThat(service.calculateDailyChange(BigDecimal.ONE, null)).isNull();
        assertThat(service.calculateDailyChange(new BigDecimal("10"), new BigDecimal("7")))
                .isEqualByComparingTo("3");
    }

    @Test
    void calculateDailyChangePercent_handlesNullsAndZeroPrev() {
        assertThat(service.calculateDailyChangePercent(null, BigDecimal.ONE)).isNull();
        assertThat(service.calculateDailyChangePercent(BigDecimal.ONE, null)).isNull();
        assertThat(service.calculateDailyChangePercent(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
        // (110-100)/100*100 = 10
        assertThat(service.calculateDailyChangePercent(new BigDecimal("110"), new BigDecimal("100")))
                .isEqualByComparingTo("10.000000");
    }

    @Test
    void resolveType_byPrefix() {
        assertThat(service.resolveType("TRB123456T17")).isEqualTo("Hazine Bonosu");
        assertThat(service.resolveType("TRT123456T17")).isEqualTo("Devlet Tahvili");
        assertThat(service.resolveType("XS123")).isEqualTo("DİBS");
        assertThat(service.resolveType(null)).isEqualTo("DİBS");
    }

    // ── refresh / evict no-op coverage ────────────────────────────────────────

    @Test
    void refreshEvdsBondsAll_reloadsList() {
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of());
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());

        assertThat(service.refreshEvdsBondsAll()).isEmpty();
        verify(evdsBondPort, times(1)).fetchBondSeriesList();
    }

    @Test
    void evictAllBondCaches_doesNotThrow() {
        service.evictAllBondCaches();
        verify(evdsBondPort, never()).fetchBondSeriesList();
    }
}
