package com.finance.portal.market.application.bond.evds;

import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.evds.model.BondCurrency;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesInfo;
import com.finance.portal.market.application.bond.evds.model.EvdsSeriesPoint;
import com.finance.portal.market.application.bond.evds.port.EvdsBondPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage complement to {@link EvdsBondServiceTest}.
 *
 * <p>Targets branches the original test never exercises:
 * <ul>
 *   <li>{@code dibsClassification.categoryOf(...)} returning a NON-null category
 *       (authoritative path) — original always returns null (heuristic fallback).</li>
 *   <li>CAPITALIZING_INDICATOR exclusion (FIXED_COUPON family + indicator &gt; 130)
 *       and its boundary (= 130 → NOT excluded).</li>
 *   <li>isZeroCoupon arm: coupon series present but category is zero-coupon → coupon NOT fetched.</li>
 *   <li>lease-certificate type label arm ({@code isLeaseCertificate()} true).</li>
 *   <li>GOLD currency arm of currencyFor (via authoritative category).</li>
 *   <li>fetchLatestCouponRate empty-list → null.</li>
 *   <li>leaseCertDataGroup blank guard arm.</li>
 *   <li>active-series comparator code-fallback + LocalDate.MAX arms.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvdsBondServiceMoreTest {

    @Mock
    private EvdsBondPort evdsBondPort;

    @Mock
    private TcmbDibsClassificationService dibsClassification;

    private ExecutorService executor;
    private EvdsBondService service;

    @BeforeEach
    void setUp() {
        executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        // LKG pass-through: resilient(...) just invokes the supplier.
        LastKnownGoodCache lkg = mock(LastKnownGoodCache.class);
        lenient().when(lkg.resilient(anyString(), any(Duration.class), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
        service = new EvdsBondService(evdsBondPort, executor, lkg, dibsClassification);
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

    private EvdsSeriesInfo valueSeries(String instrumentCode, LocalDate issue, LocalDate maturity) {
        String name = instrumentCode + " ( " + fmt(issue) + " " + fmt(maturity) + " ) Değer (121T2)";
        return new EvdsSeriesInfo("TP." + instrumentCode, "bie_pydibs", name, null, "GÜNLÜK", issue, maturity);
    }

    /** Value series with NO parseable name dates (forces code/END_DATE precedence). */
    private EvdsSeriesInfo valueSeriesNoNameDates(String instrumentCode, LocalDate startDate, LocalDate endDate) {
        String name = instrumentCode + " Değer";
        return new EvdsSeriesInfo("TP." + instrumentCode, "bie_pydibs", name, null, "GÜNLÜK", startDate, endDate);
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

    // ── Authoritative classification (categoryOf != null) ─────────────────────

    @Test
    void buildInstrument_usesAuthoritativeCategory_whenClassificationServiceReturnsNonNull() {
        // categoryOf returns a real category → BondClassifier.classify branch is skipped.
        // GOLD_INDEXED_BOND also exercises the GOLD arm of currencyFor and is NOT in the
        // FIXED_COUPON cap family, so the indicator-cap exclusion does not fire.
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "1500.00")));
        when(dibsClassification.categoryOf("TRT240227T17")).thenReturn(BondCategory.GOLD_INDEXED_BOND);

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        EvdsBondInstrument bond = result.get(0);
        assertThat(bond.getCategory()).isEqualTo(BondCategory.GOLD_INDEXED_BOND);
        assertThat(bond.getCurrency()).isEqualTo(BondCurrency.GOLD);
    }

    // ── CAPITALIZING_INDICATOR exclusion + boundary ───────────────────────────

    @Test
    void buildInstrument_excludesCapitalizingFixedCoupon_whenIndicatorAboveThreshold() {
        // FIXED_COUPON_BOND ∈ FIXED_COUPON_FAMILY and indicator 142 > 130 → excluded (returns null).
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "142.00")));
        when(dibsClassification.categoryOf("TRT240227T17")).thenReturn(BondCategory.FIXED_COUPON_BOND);

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).isEmpty();
    }

    @Test
    void buildInstrument_keepsFixedCoupon_atThresholdBoundary() {
        // indicator == 130 → compareTo(130) > 0 is FALSE → NOT excluded (boundary arm).
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "130")));
        when(dibsClassification.categoryOf("TRT240227T17")).thenReturn(BondCategory.FIXED_COUPON_BOND);

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIndicatorValue()).isEqualByComparingTo("130");
        assertThat(result.get(0).getCategory()).isEqualTo(BondCategory.FIXED_COUPON_BOND);
    }

    // ── isZeroCoupon arm: coupon series present but category is zero-coupon ────

    @Test
    void buildInstrument_zeroCouponCategory_doesNotFetchCouponRate_evenWithCouponSeries() {
        // Coupon (.ORAN) series present → hasCoupon=true, BUT category ZERO_COUPON_BOND
        // makes isZeroCoupon=true → couponRate stays null, fetchCouponRates never called.
        EvdsSeriesInfo value = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        EvdsSeriesInfo coupon = couponSeries("TRT240227T17");
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(value, coupon));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "95.00")));
        when(dibsClassification.categoryOf("TRT240227T17")).thenReturn(BondCategory.ZERO_COUPON_BOND);

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouponRate()).isNull();
        verify(evdsBondPort, never()).fetchCouponRates(anyString(), any(), any());
    }

    // ── lease certificate type label arm ──────────────────────────────────────

    @Test
    void buildInstrument_leaseCertificateCategory_setsKiraSertifikasiType() {
        // LEASE_CERTIFICATE → isLeaseCertificate() true → type "Kira Sertifikası" (not resolveType()).
        EvdsSeriesInfo s1 = valueSeries("TRD070727K10",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRD070727K10"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));
        when(dibsClassification.categoryOf("TRD070727K10")).thenReturn(BondCategory.LEASE_CERTIFICATE);

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("Kira Sertifikası");
        assertThat(result.get(0).getCategory()).isEqualTo(BondCategory.LEASE_CERTIFICATE);
    }

    // ── fetchLatestCouponRate: empty list → null ──────────────────────────────

    @Test
    void buildInstrument_couponPayingCategory_emptyCouponPoints_yieldsNullCouponRate() {
        // FIXED_COUPON_BOND pays coupon (not zero) → fetchCouponRates called, but empty → null.
        EvdsSeriesInfo value = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        EvdsSeriesInfo coupon = couponSeries("TRT240227T17");
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(value, coupon));
        when(evdsBondPort.fetchSeriesList("bie_pyks")).thenReturn(List.of());
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));
        when(dibsClassification.categoryOf("TRT240227T17")).thenReturn(BondCategory.FIXED_COUPON_BOND);
        when(evdsBondPort.fetchCouponRates(eq("TRT240227T17"), any(), any())).thenReturn(List.of());

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouponRate()).isNull();
        verify(evdsBondPort).fetchCouponRates(eq("TRT240227T17"), any(), any());
    }

    // ── leaseCertDataGroup blank guard ────────────────────────────────────────

    @Test
    void loadAllBonds_blankLeaseCertDataGroup_skipsPyksFetch() {
        ReflectionTestUtils.setField(service, "leaseCertDataGroup", "   ");
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));

        List<EvdsBondInstrument> result = service.getEvdsBondsAll();

        assertThat(result).hasSize(1);
        verify(evdsBondPort, never()).fetchSeriesList(anyString());
    }

    // ── active-series comparator: code fallback + LocalDate.MAX arms ───────────

    @Test
    void fetchActiveBondSeries_sortFallsBackToCodeAndMax_whenNameHasNoDates() {
        // codeOnly: name has no dates but code TRT240227T17 → 2027-02-24 (active, sorts by code).
        EvdsSeriesInfo codeOnly = valueSeriesNoNameDates("TRT240227T17",
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        // noDates: neither name nor code parseable (XS...) but END_DATE in future → active,
        // comparator falls to LocalDate.MAX → sorts last.
        EvdsSeriesInfo noDates = valueSeriesNoNameDates("XS1234567890",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(5));

        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(noDates, codeOnly));

        List<EvdsSeriesInfo> active = service.fetchActiveBondSeries();

        assertThat(active).extracting(EvdsSeriesInfo::extractInstrumentCode)
                .containsExactly("TRT240227T17", "XS1234567890");
    }

    @Test
    void getEvdsBondDetail_authoritativeCategory_returnsInstrument() {
        // detail path also routes through categoryOf != null branch.
        EvdsSeriesInfo s1 = valueSeries("TRT240227T17",
                LocalDate.now().minusYears(1), LocalDate.now().plusYears(2));
        when(evdsBondPort.fetchBondSeriesList()).thenReturn(List.of(s1));
        when(evdsBondPort.fetchIndicatorValues(eq("TRT240227T17"), any(), any()))
                .thenReturn(List.of(pt(LocalDate.now().minusDays(1), "100.00")));
        when(dibsClassification.categoryOf("TRT240227T17")).thenReturn(BondCategory.TLREF_INDEXED_BOND);

        EvdsBondInstrument bond = service.getEvdsBondDetail("TRT240227T17");

        assertThat(bond).isNotNull();
        assertThat(bond.getCategory()).isEqualTo(BondCategory.TLREF_INDEXED_BOND);
    }
}
