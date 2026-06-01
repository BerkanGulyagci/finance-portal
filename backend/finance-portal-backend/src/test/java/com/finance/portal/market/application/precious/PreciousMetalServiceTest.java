package com.finance.portal.market.application.precious;

import com.finance.portal.market.application.precious.model.BistMetalDailyPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.port.BistMetalFiyatlariPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreciousMetalServiceTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Mock
    private BistMetalFiyatlariPort metalClient;

    @InjectMocks
    private PreciousMetalService service;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static BistMetalDailyPoint point(String date, String usdOns, String tryKg,
                                             String tryGram, String eurOns, boolean valid) {
        BistMetalDailyPoint p = new BistMetalDailyPoint();
        p.setDate(date);
        p.setUsdOns(usdOns == null ? null : new BigDecimal(usdOns));
        p.setTryKg(tryKg == null ? null : new BigDecimal(tryKg));
        p.setTryGram(tryGram == null ? null : new BigDecimal(tryGram));
        p.setEurOns(eurOns == null ? null : new BigDecimal(eurOns));
        p.setValidPrice(valid);
        return p;
    }

    // ── getSpot ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("spot: maps latest point and sets official non-stale metadata")
    void getSpot_mapsLatestPoint() {
        BistMetalDailyPoint latest = point("2026-05-30", "950.5", "1850000", "1850.0", "880.2", true);
        when(metalClient.fetchLatestValidPoint(PreciousMetalType.PLATINUM)).thenReturn(latest);

        PreciousMetalSpotResponse resp = service.getSpot(PreciousMetalType.PLATINUM);

        assertThat(resp.getMetalType()).isEqualTo(PreciousMetalType.PLATINUM);
        assertThat(resp.getMetalName()).isEqualTo("Platin");
        assertThat(resp.getSource()).isEqualTo("Borsa İstanbul");
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.isFallback()).isFalse();
        assertThat(resp.isStale()).isFalse();
        assertThat(resp.getDataSourceType()).isEqualTo("BIST_METAL_FIYATLARI");
        assertThat(resp.getDisclaimer()).contains("Borsa İstanbul");
        assertThat(resp.getLastUpdated()).isNotNull();

        assertThat(resp.getLastValidDate()).isEqualTo("2026-05-30");
        assertThat(resp.getUsdOns()).isEqualByComparingTo("950.5");
        assertThat(resp.getTryKg()).isEqualByComparingTo("1850000");
        assertThat(resp.getTryGram()).isEqualByComparingTo("1850.0");
        assertThat(resp.getEurOns()).isEqualByComparingTo("880.2");
    }

    @Test
    @DisplayName("spot: null latest point -> stale=true with no price fields")
    void getSpot_nullLatest_stale() {
        when(metalClient.fetchLatestValidPoint(PreciousMetalType.PALLADIUM)).thenReturn(null);

        PreciousMetalSpotResponse resp = service.getSpot(PreciousMetalType.PALLADIUM);

        assertThat(resp.isStale()).isTrue();
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.getMetalName()).isEqualTo("Paladyum");
        assertThat(resp.getUsdOns()).isNull();
        assertThat(resp.getTryKg()).isNull();
        assertThat(resp.getTryGram()).isNull();
        assertThat(resp.getEurOns()).isNull();
        assertThat(resp.getLastValidDate()).isNull();
    }

    // ── getHistory ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("history: filters out points with validPrice=false")
    void getHistory_filtersInvalidPrices() {
        List<BistMetalDailyPoint> raw = List.of(
                point("2026-05-28", "900", "1800000", "1800", "850", true),
                point("2026-05-29", "0", "0", "0", "0", false),
                point("2026-05-30", "910", "1810000", "1810", "860", true));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(raw);

        PreciousMetalHistoryResponse resp = service.getHistory(PreciousMetalType.GOLD, "1W", "TRY");

        assertThat(resp.getPoints()).hasSize(2);
        assertThat(resp.getPoints()).extracting(PreciousMetalHistoryPoint::getDate)
                .containsExactly("2026-05-28", "2026-05-30");
    }

    @Test
    @DisplayName("history: TRY currency populates value from tryGram and copies all price fields")
    void getHistory_tryCurrencyUsesGram() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of(point("2026-05-30", "910", "1810000", "1810", "860", true)));

        PreciousMetalHistoryResponse resp = service.getHistory(PreciousMetalType.GOLD, "1W", "TRY");

        assertThat(resp.getCurrency()).isEqualTo("TRY");
        PreciousMetalHistoryPoint pt = resp.getPoints().get(0);
        assertThat(pt.getValue()).isEqualByComparingTo("1810");   // tryGram
        assertThat(pt.getTryGram()).isEqualByComparingTo("1810");
        assertThat(pt.getTryKg()).isEqualByComparingTo("1810000");
        assertThat(pt.getUsdOns()).isEqualByComparingTo("910");
        assertThat(pt.getEurOns()).isEqualByComparingTo("860");
    }

    @Test
    @DisplayName("history: USD currency populates value from usdOns")
    void getHistory_usdCurrencyUsesOns() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of(point("2026-05-30", "910", "1810000", "1810", "860", true)));

        PreciousMetalHistoryResponse resp = service.getHistory(PreciousMetalType.GOLD, "1W", "USD");

        assertThat(resp.getCurrency()).isEqualTo("USD");
        assertThat(resp.getPoints().get(0).getValue()).isEqualByComparingTo("910"); // usdOns
    }

    @Test
    @DisplayName("history: EUR currency populates value from eurOns")
    void getHistory_eurCurrencyUsesOns() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of(point("2026-05-30", "910", "1810000", "1810", "860", true)));

        PreciousMetalHistoryResponse resp = service.getHistory(PreciousMetalType.GOLD, "1W", "EUR");

        assertThat(resp.getCurrency()).isEqualTo("EUR");
        assertThat(resp.getPoints().get(0).getValue()).isEqualByComparingTo("860"); // eurOns
    }

    @Test
    @DisplayName("history: lowercase currency is uppercased + still resolves the right field")
    void getHistory_currencyCaseInsensitive() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of(point("2026-05-30", "910", "1810000", "1810", "860", true)));

        PreciousMetalHistoryResponse resp = service.getHistory(PreciousMetalType.GOLD, "1W", "usd");

        assertThat(resp.getCurrency()).isEqualTo("USD");
        assertThat(resp.getPoints().get(0).getValue()).isEqualByComparingTo("910");
    }

    @Test
    @DisplayName("history: null currency defaults to TRY")
    void getHistory_nullCurrencyDefaultsToTry() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of(point("2026-05-30", "910", "1810000", "1810", "860", true)));

        PreciousMetalHistoryResponse resp = service.getHistory(PreciousMetalType.GOLD, "1W", null);

        assertThat(resp.getCurrency()).isEqualTo("TRY");
        assertThat(resp.getPoints().get(0).getValue()).isEqualByComparingTo("1810");
    }

    @Test
    @DisplayName("history: empty upstream yields empty points list, metadata still populated")
    void getHistory_emptyUpstream() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        PreciousMetalHistoryResponse resp = service.getHistory(PreciousMetalType.SILVER, "3M", "TRY");

        assertThat(resp.getPoints()).isEmpty();
        assertThat(resp.getMetalType()).isEqualTo(PreciousMetalType.SILVER);
        assertThat(resp.getMetalName()).isEqualTo("Gümüş");
        assertThat(resp.getRange()).isEqualTo("3M");
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.isFallback()).isFalse();
        assertThat(resp.getDataSourceType()).isEqualTo("BIST_METAL_FIYATLARI");
    }

    // ── range → date window ──────────────────────────────────────────────────

    @Test
    @DisplayName("history range '1Y' fetches a one-year window ending today")
    void getHistory_rangeOneYear_dates() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of());

        service.getHistory(PreciousMetalType.GOLD, "1Y", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> end = ArgumentCaptor.forClass(String.class);
        verify(metalClient).fetchMetalPrices(eq(PreciousMetalType.GOLD), start.capture(), end.capture());

        LocalDate today = LocalDate.now(ISTANBUL);
        assertThat(start.getValue()).isEqualTo(today.minusYears(1).format(FMT));
        assertThat(end.getValue()).isEqualTo(today.format(FMT));
    }

    @Test
    @DisplayName("history range 'ALL' starts at 2011-01-01")
    void getHistory_rangeAll_startsAt2011() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of());

        service.getHistory(PreciousMetalType.GOLD, "ALL", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(metalClient).fetchMetalPrices(eq(PreciousMetalType.GOLD), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo("2011-01-01");
    }

    @Test
    @DisplayName("history range null/unknown defaults to a one-month window")
    void getHistory_rangeDefaultsToOneMonth() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.GOLD), anyString(), anyString()))
                .thenReturn(List.of());

        service.getHistory(PreciousMetalType.GOLD, null, "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(metalClient).fetchMetalPrices(eq(PreciousMetalType.GOLD), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusMonths(1).format(FMT));
    }

    @Test
    @DisplayName("evictPreciousCaches runs without touching the metal client")
    void evictPreciousCaches_noClientInteraction() {
        service.evictPreciousCaches();
        verify(metalClient, org.mockito.Mockito.never()).fetchLatestValidPoint(any());
    }
}
