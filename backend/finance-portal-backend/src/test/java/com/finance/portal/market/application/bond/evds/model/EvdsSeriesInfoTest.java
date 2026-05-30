package com.finance.portal.market.application.bond.evds.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EvdsSeriesInfo#parseCbrtCode()} ve mevcut date parser'ları için characterization.
 */
class EvdsSeriesInfoTest {

    private static EvdsSeriesInfo of(String name) {
        return new EvdsSeriesInfo("TP.X", "bie_pydibs", name, null, "GÜNLÜK",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
    }

    // ── parseCbrtCode ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sabit kuponlu tam DT: '... Değer (121T2)' → 121T2")
    void cbrt_fixedCoupon() {
        assertThat(of("TRT240227T17 ( 24.02.2023 24.02.2027 ) Değer (121T2)").parseCbrtCode())
                .isEqualTo("121T2");
    }

    @Test
    @DisplayName("Kupon stripi: '... Değer (121T2K19240227)' → 121T2K19240227")
    void cbrt_couponStrip() {
        assertThat(of("TRT260826K19 ( 24.02.2023 26.08.2026 ) Değer (121T2K19240227)").parseCbrtCode())
                .isEqualTo("121T2K19240227");
    }

    @Test
    @DisplayName("TÜFE-endeksli: '... Değer (61T4DK13010328)' → 61T4DK13010328")
    void cbrt_inflationIndexed() {
        assertThat(of("TRT030626K26 ( 08.03.2023 03.06.2026 ) Değer (61T4DK13010328)").parseCbrtCode())
                .isEqualTo("61T4DK13010328");
    }

    @Test
    @DisplayName("Sadece tarih parantezi varsa CBRT yok → null")
    void cbrt_noCbrtBlock() {
        assertThat(of("TRB170626T13 ( 17.06.2025 17.06.2026 ) Değer").parseCbrtCode())
                .isNull();
    }

    @Test
    @DisplayName("seriesName null → null")
    void cbrt_nullName() {
        assertThat(of(null).parseCbrtCode()).isNull();
    }

    // ── parseIssueDateFromName / parseMaturityDateFromName ───────────────────

    @Test
    @DisplayName("Tarih parsing — ihraç ve vade ayrı çıkar")
    void dateParsing() {
        EvdsSeriesInfo info = of("TRT030626K26 ( 08.03.2023 03.06.2026 ) Değer (61T4DK13010328)");
        assertThat(info.parseIssueDateFromName()).isEqualTo(LocalDate.of(2023, 3, 8));
        assertThat(info.parseMaturityDateFromName()).isEqualTo(LocalDate.of(2026, 6, 3));
    }
}
