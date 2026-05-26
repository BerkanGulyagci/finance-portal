package com.finance.portal.portfolio.service.support;

import com.finance.portal.market.application.precious.PreciousMetalHistoryPoint;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioHistoryPointsTest {

    // ------------------------------ silverWindow ------------------------------

    @Test
    @DisplayName("silverWindow: null yanıt → latest=null, prev=null")
    void silverWindow_null_returnsEmptyWindow() {
        PortfolioHistoryPoints.SilverWindow w = PortfolioHistoryPoints.silverWindow(null);

        assertThat(w.latest()).isNull();
        assertThat(w.prev()).isNull();
    }

    @Test
    @DisplayName("silverWindow: boş points → latest=null, prev=null")
    void silverWindow_emptyPoints_returnsEmptyWindow() {
        SilverHistoryResponse resp = new SilverHistoryResponse();
        resp.setPoints(List.of());

        PortfolioHistoryPoints.SilverWindow w = PortfolioHistoryPoints.silverWindow(resp);

        assertThat(w.latest()).isNull();
        assertThat(w.prev()).isNull();
    }

    @Test
    @DisplayName("silverWindow: tek nokta → latest dolu, prev=null")
    void silverWindow_singlePoint_latestOnly() {
        SilverHistoryPoint p = new SilverHistoryPoint();
        p.setDate("2026-05-26");
        SilverHistoryResponse resp = new SilverHistoryResponse();
        resp.setPoints(List.of(p));

        PortfolioHistoryPoints.SilverWindow w = PortfolioHistoryPoints.silverWindow(resp);

        assertThat(w.latest()).isSameAs(p);
        assertThat(w.prev()).isNull();
    }

    @Test
    @DisplayName("silverWindow: 2+ nokta → latest = son, prev = sondan bir önceki")
    void silverWindow_multiPoints_returnsLastTwo() {
        SilverHistoryPoint older = new SilverHistoryPoint();
        older.setDate("2026-05-24");
        SilverHistoryPoint prev = new SilverHistoryPoint();
        prev.setDate("2026-05-25");
        SilverHistoryPoint latest = new SilverHistoryPoint();
        latest.setDate("2026-05-26");
        SilverHistoryResponse resp = new SilverHistoryResponse();
        // Mutable list — Silver model bunu kabul ediyor
        List<SilverHistoryPoint> pts = new ArrayList<>();
        pts.add(older);
        pts.add(prev);
        pts.add(latest);
        resp.setPoints(pts);

        PortfolioHistoryPoints.SilverWindow w = PortfolioHistoryPoints.silverWindow(resp);

        assertThat(w.latest()).isSameAs(latest);
        assertThat(w.prev()).isSameAs(prev);
    }

    // ------------------------------ preciousWindow ------------------------------

    @Test
    @DisplayName("preciousWindow: null → boş")
    void preciousWindow_null_returnsEmptyWindow() {
        PortfolioHistoryPoints.PreciousWindow w = PortfolioHistoryPoints.preciousWindow(null);

        assertThat(w.latest()).isNull();
        assertThat(w.prev()).isNull();
    }

    @Test
    @DisplayName("preciousWindow: 2+ nokta → latest + prev")
    void preciousWindow_multiPoints_returnsLastTwo() {
        PreciousMetalHistoryPoint a = new PreciousMetalHistoryPoint();
        a.setDate("2026-05-25");
        PreciousMetalHistoryPoint b = new PreciousMetalHistoryPoint();
        b.setDate("2026-05-26");
        PreciousMetalHistoryResponse resp = new PreciousMetalHistoryResponse();
        List<PreciousMetalHistoryPoint> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        resp.setPoints(pts);

        PortfolioHistoryPoints.PreciousWindow w = PortfolioHistoryPoints.preciousWindow(resp);

        assertThat(w.latest()).isSameAs(b);
        assertThat(w.prev()).isSameAs(a);
    }

    // ------------------------------ preciousPointValue ------------------------------

    @Test
    @DisplayName("preciousPointValue: null point → null")
    void preciousPointValue_null_returnsNull() {
        assertThat(PortfolioHistoryPoints.preciousPointValue(null, "GRAM_TRY")).isNull();
    }

    @Test
    @DisplayName("preciousPointValue: kategori switch — her dal doğru alanı döndürür")
    void preciousPointValue_switchesCorrectly() {
        PreciousMetalHistoryPoint p = new PreciousMetalHistoryPoint();
        p.setTryGram(new BigDecimal("3060.19"));
        p.setTryKg(new BigDecimal("3060190.00"));
        p.setUsdOns(new BigDecimal("2350.00"));
        p.setEurOns(new BigDecimal("2150.00"));
        p.setValue(new BigDecimal("999.99"));

        assertThat(PortfolioHistoryPoints.preciousPointValue(p, "GRAM_TRY"))
                .isEqualByComparingTo("3060.19");
        assertThat(PortfolioHistoryPoints.preciousPointValue(p, "KG_TRY"))
                .isEqualByComparingTo("3060190.00");
        assertThat(PortfolioHistoryPoints.preciousPointValue(p, "USD_ONS"))
                .isEqualByComparingTo("2350.00");
        assertThat(PortfolioHistoryPoints.preciousPointValue(p, "EUR_ONS"))
                .isEqualByComparingTo("2150.00");
    }

    @Test
    @DisplayName("preciousPointValue: tanınmayan kategori → varsayılan value")
    void preciousPointValue_unknownCategory_fallsBackToValue() {
        PreciousMetalHistoryPoint p = new PreciousMetalHistoryPoint();
        p.setValue(new BigDecimal("42.00"));

        assertThat(PortfolioHistoryPoints.preciousPointValue(p, "UNKNOWN_CAT"))
                .isEqualByComparingTo("42.00");
    }
}
