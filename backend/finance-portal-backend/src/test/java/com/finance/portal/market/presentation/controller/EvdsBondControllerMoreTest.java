package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.presentation.dto.EvdsBondHistoryPointDto;
import com.finance.portal.market.presentation.dto.EvdsBondItemDto;
import com.finance.portal.market.presentation.dto.EvdsBondPageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tamamlayıcısı: {@link EvdsBondController} için doğrudan-çağrı (MockMvc'siz) testleri.
 *
 * <p>Bunlar {@link EvdsBondControllerTest}'in ATLADIĞI dalları hedefler:
 * geçersiz sortDir → 400, sayfa/boyut clamp, sayfa sınırı dışı (boş slice), her applyFilters kolu
 * (search eşleşir/eşleşmez/boş/instrumentCode-null, type eşleşir/eşleşmez/boş, category eşleşir/
 * eşleşmez/null, min/max gün sınırları), her applySort switch case + desc tersleme + null-coalesce
 * kolları, category null → "UNKNOWN", detay IllegalArgumentException → 404, normalizeCode null,
 * history (geçerli/varsayılan/geçersiz period, boş sonuç, küçük-harf normalize).
 *
 * <p>Doğrudan controller çağrısı kullanılır (ResponseEntity gövdesi üzerinden assert) — böylece
 * her dal kesin kontrol edilir ve Jackson serileştirme yoluna girilmez.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvdsBondControllerMoreTest {

    @Mock
    EvdsBondService evdsBondService;

    @InjectMocks
    EvdsBondController controller;

    // ── Fixtures ────────────────────────────────────────────────────────────

    private EvdsBondInstrument bond(String code) {
        EvdsBondInstrument b = new EvdsBondInstrument();
        b.setInstrumentCode(code);
        b.setType("Devlet Tahvili");
        b.setCategory(BondCategory.FIXED_COUPON_BOND);
        b.setIssueDate(LocalDate.of(2024, 1, 10));
        b.setMaturityDate(LocalDate.of(2027, 1, 10));
        b.setRemainingDays(400);
        b.setIndicatorValue(new BigDecimal("98.50"));
        b.setPreviousValue(new BigDecimal("98.10"));
        b.setDailyChangePercent(new BigDecimal("0.41"));
        b.setCouponRate(new BigDecimal("12.00"));
        b.setSource("TCMB EVDS");
        return b;
    }

    /** Tüm opsiyonel BigDecimal/LocalDate alanları null — sort comparator null-coalesce kollarını tetikler. */
    private EvdsBondInstrument nullFieldsBond(String code, int remainingDays) {
        EvdsBondInstrument b = new EvdsBondInstrument();
        b.setInstrumentCode(code);
        b.setType(null);
        b.setCategory(null);
        b.setIssueDate(null);
        b.setMaturityDate(null);
        b.setRemainingDays(remainingDays);
        b.setIndicatorValue(null);
        b.setPreviousValue(null);
        b.setDailyChangePercent(null);
        b.setCouponRate(null);
        b.setSource("TCMB EVDS");
        return b;
    }

    private EvdsBondPageResponse pageBody(ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp) {
        ApiResponse<EvdsBondPageResponse> body = resp.getBody();
        assertThat(body).isNotNull();
        return body.getData();
    }

    // ── getBonds: validation dalları ─────────────────────────────────────────

    @Test
    void getBonds_invalidSortDir_returns400() {
        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", null, null, "maturityDate", "sideways");

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("Geçersiz sortDir");
    }

    @Test
    void getBonds_sortDirDescUppercase_isAccepted() {
        // "DESC" → equalsIgnoreCase("desc") true → validation geçer, ayrıca applySort reversed dalı.
        when(evdsBondService.getEvdsBondsAll())
                .thenReturn(List.of(bond("AAA"), bond("CCC"), bond("BBB")));

        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", null, null, "instrumentCode", "DESC");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        List<EvdsBondItemDto> items = pageBody(resp).getItems();
        assertThat(items).extracting(EvdsBondItemDto::getInstrumentCode)
                .containsExactly("CCC", "BBB", "AAA");
    }

    // ── getBonds: page/size clamp + sınır dışı ──────────────────────────────

    @Test
    void getBonds_sizeAndPageClamped_andEmptyServiceList() {
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of());

        // size=999 → 100'e clamp, page=-3 → 0'a clamp; boş listede totalItems=0.
        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                -3, 999, "", "", "", null, null, "maturityDate", "asc");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        EvdsBondPageResponse body = pageBody(resp);
        assertThat(body.getSize()).isEqualTo(100);
        assertThat(body.getPage()).isEqualTo(0);
        assertThat(body.getTotalItems()).isEqualTo(0L);
        assertThat(body.getItems()).isEmpty();
    }

    @Test
    void getBonds_sizeBelowOne_clampedToOne() {
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(bond("AAA"), bond("BBB")));

        // size=0 → Math.max(0,1)=1 → tek öğe döner.
        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 0, "", "", "", null, null, "instrumentCode", "asc");

        EvdsBondPageResponse body = pageBody(resp);
        assertThat(body.getSize()).isEqualTo(1);
        assertThat(body.getItems()).hasSize(1);
        assertThat(body.getItems().get(0).getInstrumentCode()).isEqualTo("AAA");
        assertThat(body.getTotalItems()).isEqualTo(2L);
    }

    @Test
    void getBonds_pageBeyondTotal_returnsEmptySlice() {
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(bond("AAA"), bond("BBB")));

        // page=5, size=50 → fromIdx=250 >= totalItems(2) → List.of() dalı.
        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                5, 50, "", "", "", null, null, "maturityDate", "asc");

        EvdsBondPageResponse body = pageBody(resp);
        assertThat(body.getItems()).isEmpty();
        assertThat(body.getTotalItems()).isEqualTo(2L);
    }

    // ── getBonds: applyFilters her kolu ──────────────────────────────────────

    @Test
    void getBonds_searchFilter_matchesAndExcludes() {
        EvdsBondInstrument match = bond("TRT123ABC");
        EvdsBondInstrument noMatch = bond("XYZ999");
        EvdsBondInstrument nullCode = bond(null); // instrumentCode null → search dalında false döner
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(match, noMatch, nullCode));

        // search "abc" → trim+toUpperCase "ABC" → sadece TRT123ABC içerir.
        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "  abc ", "", "", null, null, "instrumentCode", "asc");

        List<EvdsBondItemDto> items = pageBody(resp).getItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getInstrumentCode()).isEqualTo("TRT123ABC");
    }

    @Test
    void getBonds_typeFilter_matchAndMismatch() {
        EvdsBondInstrument dt = bond("AAA"); // type "Devlet Tahvili"
        EvdsBondInstrument hb = bond("BBB");
        hb.setType("Hazine Bonosu");
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(dt, hb));

        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "devlet tahvili", "", null, null, "instrumentCode", "asc");

        List<EvdsBondItemDto> items = pageBody(resp).getItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getInstrumentCode()).isEqualTo("AAA");
    }

    @Test
    void getBonds_categoryFilter_matchMismatchAndNullCategory() {
        EvdsBondInstrument fixed = bond("AAA"); // FIXED_COUPON_BOND
        EvdsBondInstrument zero = bond("BBB");
        zero.setCategory(BondCategory.ZERO_COUPON_BILL);
        EvdsBondInstrument nullCat = bond("CCC");
        nullCat.setCategory(null); // category null → filtre dalında dışlanır
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(fixed, zero, nullCat));

        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "fixed_coupon_bond", null, null, "instrumentCode", "asc");

        List<EvdsBondItemDto> items = pageBody(resp).getItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getInstrumentCode()).isEqualTo("AAA");
    }

    @Test
    void getBonds_remainingDaysMinAndMax_boundaries() {
        EvdsBondInstrument shortB = bond("AAA");
        shortB.setRemainingDays(10);
        EvdsBondInstrument midB = bond("BBB");
        midB.setRemainingDays(100);
        EvdsBondInstrument longB = bond("CCC");
        longB.setRemainingDays(1000);
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(shortB, midB, longB));

        // minDays=50 (shortB<50 dışlanır), maxDays=500 (longB>500 dışlanır) → sadece midB.
        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", 50, 500, "instrumentCode", "asc");

        List<EvdsBondItemDto> items = pageBody(resp).getItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getInstrumentCode()).isEqualTo("BBB");
    }

    // ── getBonds: applySort her switch case + null-coalesce ─────────────────

    @Test
    void getBonds_sortByIssueDate_asc() {
        EvdsBondInstrument early = bond("AAA");
        early.setIssueDate(LocalDate.of(2020, 1, 1));
        EvdsBondInstrument late = bond("BBB");
        late.setIssueDate(LocalDate.of(2025, 1, 1));
        EvdsBondInstrument nullDate = nullFieldsBond("CCC", 5); // issueDate null → "" fallback
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(late, early, nullDate));

        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", null, null, "issueDate", "asc");

        assertThat(pageBody(resp).getItems())
                .extracting(EvdsBondItemDto::getInstrumentCode)
                .containsExactly("CCC", "AAA", "BBB"); // "" < "2020..." < "2025..."
    }

    @Test
    void getBonds_sortByRemainingDays_asc() {
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(
                nullFieldsBond("HIGH", 900),
                nullFieldsBond("LOW", 5),
                nullFieldsBond("MID", 100)));

        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", null, null, "remainingDays", "asc");

        assertThat(pageBody(resp).getItems())
                .extracting(EvdsBondItemDto::getInstrumentCode)
                .containsExactly("LOW", "MID", "HIGH");
    }

    @Test
    void getBonds_sortByIndicatorValue_nullCoalescesToZero() {
        EvdsBondInstrument high = bond("HIGH");
        high.setIndicatorValue(new BigDecimal("105.00"));
        EvdsBondInstrument nullVal = nullFieldsBond("NULL", 10); // indicatorValue null → BigDecimal.ZERO
        EvdsBondInstrument low = bond("LOW");
        low.setIndicatorValue(new BigDecimal("50.00"));
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(high, low, nullVal));

        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", null, null, "indicatorValue", "asc");

        assertThat(pageBody(resp).getItems())
                .extracting(EvdsBondItemDto::getInstrumentCode)
                .containsExactly("NULL", "LOW", "HIGH"); // 0 < 50 < 105
    }

    @Test
    void getBonds_sortByDailyChangePercent_nullCoalescesToZero() {
        EvdsBondInstrument pos = bond("POS");
        pos.setDailyChangePercent(new BigDecimal("2.00"));
        EvdsBondInstrument nullPct = nullFieldsBond("NULL", 10); // null → ZERO
        EvdsBondInstrument neg = bond("NEG");
        neg.setDailyChangePercent(new BigDecimal("-1.00"));
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(pos, neg, nullPct));

        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", null, null, "dailyChangePercent", "asc");

        assertThat(pageBody(resp).getItems())
                .extracting(EvdsBondItemDto::getInstrumentCode)
                .containsExactly("NEG", "NULL", "POS"); // -1 < 0 < 2
    }

    @Test
    void getBonds_sortByCouponRate_nullCoalescesToZero() {
        EvdsBondInstrument hi = bond("HI");
        hi.setCouponRate(new BigDecimal("15.00"));
        EvdsBondInstrument nullCoupon = nullFieldsBond("NULL", 10); // null → ZERO
        EvdsBondInstrument lo = bond("LO");
        lo.setCouponRate(new BigDecimal("8.00"));
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(hi, lo, nullCoupon));

        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", null, null, "couponRate", "asc");

        assertThat(pageBody(resp).getItems())
                .extracting(EvdsBondItemDto::getInstrumentCode)
                .containsExactly("NULL", "LO", "HI"); // 0 < 8 < 15
    }

    @Test
    void getBonds_sortByMaturityDate_default_withNullDate() {
        EvdsBondInstrument late = bond("LATE");
        late.setMaturityDate(LocalDate.of(2030, 1, 1));
        EvdsBondInstrument nullMat = nullFieldsBond("NULLMAT", 5); // maturityDate null → "" fallback
        EvdsBondInstrument early = bond("EARLY");
        early.setMaturityDate(LocalDate.of(2026, 1, 1));
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(late, nullMat, early));

        // sortBy "maturityDate" → switch default kolu.
        ResponseEntity<ApiResponse<EvdsBondPageResponse>> resp = controller.getBonds(
                0, 50, "", "", "", null, null, "maturityDate", "asc");

        assertThat(pageBody(resp).getItems())
                .extracting(EvdsBondItemDto::getInstrumentCode)
                .containsExactly("NULLMAT", "EARLY", "LATE"); // "" < 2026 < 2030
    }

    // ── getCategoryCounts: category null → "UNKNOWN" ─────────────────────────

    @Test
    void getCategoryCounts_nullCategory_bucketedAsUnknown() {
        EvdsBondInstrument fixed = bond("AAA"); // FIXED_COUPON_BOND
        EvdsBondInstrument nullCat = bond("BBB");
        nullCat.setCategory(null);
        when(evdsBondService.getEvdsBondsAll()).thenReturn(List.of(fixed, nullCat));

        ResponseEntity<ApiResponse<Map<String, Long>>> resp = controller.getCategoryCounts();

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        Map<String, Long> counts = resp.getBody().getData();
        assertThat(counts).containsEntry("FIXED_COUPON_BOND", 1L);
        assertThat(counts).containsEntry("UNKNOWN", 1L);
    }

    // ── getBondDetail: IllegalArgumentException → 404, normalizeCode null ────

    @Test
    void getBondDetail_serviceThrowsIllegalArgument_returns404() {
        when(evdsBondService.getEvdsBondDetail("TRDBOOM"))
                .thenThrow(new IllegalArgumentException("not found"));

        ResponseEntity<ApiResponse<EvdsBondItemDto>> resp = controller.getBondDetail("trdboom");

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).isEqualTo("Kıymet bulunamadı: TRDBOOM");
    }

    @Test
    void getBondDetail_nullPathVariable_normalizesToEmpty() {
        // normalizeCode(null) → "" dalı (HTTP'den ulaşılamaz, doğrudan çağrıyla kapsanır).
        when(evdsBondService.getEvdsBondDetail("")).thenReturn(null);

        ResponseEntity<ApiResponse<EvdsBondItemDto>> resp = controller.getBondDetail(null);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody().getMessage()).isEqualTo("Kıymet bulunamadı: ");
    }

    @Test
    void getBondDetail_found_returns200() {
        when(evdsBondService.getEvdsBondDetail("TRT123ABC")).thenReturn(bond("TRT123ABC"));

        ResponseEntity<ApiResponse<EvdsBondItemDto>> resp = controller.getBondDetail("trt123abc");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().isSuccess()).isTrue();
        assertThat(resp.getBody().getData().getInstrumentCode()).isEqualTo("TRT123ABC");
    }

    // ── getBondHistory: geçerli / varsayılan / geçersiz period, boş sonuç ────

    @Test
    void getBondHistory_validPeriod_returns200WithPoints() {
        EvdsBondHistoryPoint p1 = new EvdsBondHistoryPoint(
                LocalDate.of(2025, 1, 1), "01-01-2025", "TRT123ABC", new BigDecimal("99.10"));
        EvdsBondHistoryPoint p2 = new EvdsBondHistoryPoint(
                LocalDate.of(2025, 1, 2), "02-01-2025", "TRT123ABC", new BigDecimal("99.20"));
        when(evdsBondService.getEvdsBondHistory("TRT123ABC", BondPeriod.THREE_MONTHS))
                .thenReturn(List.of(p1, p2));

        // küçük harf + boşluk → parsePeriod trim+toUpperCase → THREE_MONTHS
        ResponseEntity<ApiResponse<List<EvdsBondHistoryPointDto>>> resp =
                controller.getBondHistory("trt123abc", " three_months ");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        List<EvdsBondHistoryPointDto> dtos = resp.getBody().getData();
        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getInstrumentCode()).isEqualTo("TRT123ABC");
        assertThat(dtos.get(0).getIndicatorValue()).isEqualByComparingTo("99.10");
        assertThat(dtos.get(0).getDate()).isEqualTo("2025-01-01");
    }

    @Test
    void getBondHistory_defaultPeriod_emptyResult() {
        // period "ONE_MONTH" (controller default) → parsePeriod success, boş history dalı.
        when(evdsBondService.getEvdsBondHistory("TRT123ABC", BondPeriod.ONE_MONTH))
                .thenReturn(List.of());

        ResponseEntity<ApiResponse<List<EvdsBondHistoryPointDto>>> resp =
                controller.getBondHistory("trt123abc", "ONE_MONTH");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().isSuccess()).isTrue();
        assertThat(resp.getBody().getData()).isEmpty();
    }

    @Test
    void getBondHistory_invalidPeriod_returns400() {
        // parsePeriod("BOGUS") → valueOf throws → null → 400 dalı.
        ResponseEntity<ApiResponse<List<EvdsBondHistoryPointDto>>> resp =
                controller.getBondHistory("trt123abc", "BOGUS");

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().isSuccess()).isFalse();
        assertThat(resp.getBody().getMessage()).contains("Geçersiz period");
    }
}
