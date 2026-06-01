package com.finance.portal.market.application.silver;

import com.finance.portal.market.application.precious.model.BistMetalDailyPoint;
import com.finance.portal.market.application.precious.model.BistPreciousMetalPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.model.PriceUnit;
import com.finance.portal.market.application.precious.port.BistMetalFiyatlariPort;
import com.finance.portal.market.application.precious.port.BistPreciousMetalsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SilverMarketServiceTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Mock
    private BistPreciousMetalsPort bistClient;
    @Mock
    private BistMetalFiyatlariPort metalClient;

    @InjectMocks
    private SilverMarketService service;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static BigDecimal bd(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static BistPreciousMetalPoint tryPoint(String date, String gramWa, String gramClose,
                                                   String gramLow, String gramHigh, boolean valid) {
        BistPreciousMetalPoint p = new BistPreciousMetalPoint();
        p.setDate(date);
        p.setGramWeightedAverage(bd(gramWa));
        p.setGramClose(bd(gramClose));
        p.setGramLow(bd(gramLow));
        p.setGramHigh(bd(gramHigh));
        p.setCloseRaw(gramClose == null ? null : bd(gramClose).multiply(BigDecimal.valueOf(1000)));
        p.setWeightedAverageRaw(gramWa == null ? null : bd(gramWa).multiply(BigDecimal.valueOf(1000)));
        p.setLowRaw(gramLow == null ? null : bd(gramLow).multiply(BigDecimal.valueOf(1000)));
        p.setHighRaw(gramHigh == null ? null : bd(gramHigh).multiply(BigDecimal.valueOf(1000)));
        p.setVolumeRaw(bd("55555"));
        p.setQuantityKg(bd("5.0"));
        p.setTransactionCount(7);
        p.setValidPrice(valid);
        return p;
    }

    private static BistPreciousMetalPoint usdPoint(String date, String close, String high,
                                                   String low, String wa, boolean valid) {
        BistPreciousMetalPoint p = new BistPreciousMetalPoint();
        p.setDate(date);
        p.setCloseUsdOns(bd(close));
        p.setHighUsdOns(bd(high));
        p.setLowUsdOns(bd(low));
        p.setWeightedAverageUsdOns(bd(wa));
        p.setQuantityKg(bd("2.0"));
        p.setValidPrice(valid);
        return p;
    }

    private static BistMetalDailyPoint agPoint(String date, String usdOns, String tryKg,
                                               String tryGram, boolean valid) {
        BistMetalDailyPoint p = new BistMetalDailyPoint();
        p.setDate(date);
        p.setUsdOns(bd(usdOns));
        p.setTryKg(bd(tryKg));
        p.setTryGram(bd(tryGram));
        p.setValidPrice(valid);
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getSpotSilver
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("spot: both TRY and USD points → full mapping; gram close aligned from 1W history last point")
    void getSpotSilver_bothPoints_alignedFromHistory() {
        BistPreciousMetalPoint tryP = tryPoint("2026-05-30", "35", "35.1", "34.5", "35.5", true);
        BistPreciousMetalPoint usdP = usdPoint("2026-05-30", "32.1", "32.5", "31.8", "32.0", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.TRY_KG)).thenReturn(tryP);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.USD_ONS)).thenReturn(usdP);

        // alignGramCloseFromLatestHistory → getSilverHistory("1W","TRY") → getSilverHistoryTry
        BistPreciousMetalPoint histVst = tryPoint("2026-05-31", "36", "36.20", "35.80", "36.50", true);
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(histVst));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        SilverSpotResponse resp = service.getSpotSilver();

        assertThat(resp.getSource()).isEqualTo("Borsa İstanbul");
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.isStale()).isFalse();
        assertThat(resp.getBistDate()).isEqualTo("2026-05-30");
        // TRY raw + gram fields
        assertThat(resp.getCloseTryKg()).isEqualByComparingTo("35100"); // 35.1 * 1000
        assertThat(resp.getSilverGramTry()).isEqualByComparingTo("35");
        // USD fields
        assertThat(resp.getSilverUsdOns()).isEqualByComparingTo("32.1");
        assertThat(resp.getSilverUsdOnsHigh()).isEqualByComparingTo("32.5");
        // aligned from history last point (close 36.20 → scale 2)
        assertThat(resp.getSilverGramCloseTry()).isEqualByComparingTo("36.20");
        assertThat(resp.getSilverGramHighTry()).isEqualByComparingTo("36.50");
        assertThat(resp.getSilverGramLowTry()).isEqualByComparingTo("35.80");
        assertThat(resp.getLastValidDate()).isEqualTo("2026-05-31"); // overwritten by aligned history date
    }

    @Test
    @DisplayName("spot: only TRY point present, USD null → USD fields stay null")
    void getSpotSilver_onlyTry() {
        BistPreciousMetalPoint tryP = tryPoint("2026-05-30", "35", "35.1", "34.5", "35.5", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.TRY_KG)).thenReturn(tryP);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.USD_ONS)).thenReturn(null);
        // history empty → no align
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of());
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        SilverSpotResponse resp = service.getSpotSilver();

        assertThat(resp.isStale()).isFalse();
        assertThat(resp.getSilverGramTry()).isEqualByComparingTo("35");
        assertThat(resp.getSilverUsdOns()).isNull();
        // history empty → original spot gram close retained
        assertThat(resp.getSilverGramCloseTry()).isEqualByComparingTo("35.1");
    }

    @Test
    @DisplayName("spot: only USD point present → TRY fields null, align history skipped (empty)")
    void getSpotSilver_onlyUsd() {
        BistPreciousMetalPoint usdP = usdPoint("2026-05-30", "32.1", "32.5", "31.8", "32.0", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.TRY_KG)).thenReturn(null);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.USD_ONS)).thenReturn(usdP);
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of());
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        SilverSpotResponse resp = service.getSpotSilver();

        assertThat(resp.isStale()).isFalse();
        assertThat(resp.getSilverUsdOns()).isEqualByComparingTo("32.1");
        assertThat(resp.getCloseTryKg()).isNull();
        assertThat(resp.getSilverGramCloseTry()).isNull();
    }

    @Test
    @DisplayName("spot: both null → empty stale response, no history call")
    void getSpotSilver_bothNull_stale() {
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.TRY_KG)).thenReturn(null);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.USD_ONS)).thenReturn(null);

        SilverSpotResponse resp = service.getSpotSilver();

        assertThat(resp.isStale()).isTrue();
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.isFallback()).isFalse();
        assertThat(resp.getSilverGramTry()).isNull();
        assertThat(resp.getLastUpdated()).isNotNull();
        verify(bistClient, never()).fetchHistory(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("spot: history align last close <= 0 → alignment skipped, spot close retained")
    void getSpotSilver_alignNonPositiveClose() {
        BistPreciousMetalPoint tryP = tryPoint("2026-05-30", "35", "35.1", "34.5", "35.5", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.TRY_KG)).thenReturn(tryP);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.USD_ONS)).thenReturn(null);
        // last history close is 0 → alignment bailout
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(tryPoint("2026-05-31", "0", "0", "0", "0", true)));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        SilverSpotResponse resp = service.getSpotSilver();

        assertThat(resp.getSilverGramCloseTry()).isEqualByComparingTo("35.1");
        assertThat(resp.getLastValidDate()).isEqualTo("2026-05-30"); // not overwritten
    }

    @Test
    @DisplayName("spot: history align throws → caught, spot close retained")
    void getSpotSilver_alignThrows() {
        BistPreciousMetalPoint tryP = tryPoint("2026-05-30", "35", "35.1", "34.5", "35.5", true);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.TRY_KG)).thenReturn(tryP);
        when(bistClient.fetchLatestValidPoint(PreciousMetalType.SILVER, PriceUnit.USD_ONS)).thenReturn(null);
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenThrow(new RuntimeException("history down"));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        SilverSpotResponse resp = service.getSpotSilver();

        assertThat(resp.getSilverGramCloseTry()).isEqualByComparingTo("35.1");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  getSilverHistory — routing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("history ALL/TRY → AG metal-ref endpoint, gram fields + synthetic open, invalid filtered")
    void getSilverHistory_allTry_metalRef() {
        List<BistMetalDailyPoint> raw = List.of(
                agPoint("2026-05-28", "30", "1700000", "1.70", true),
                agPoint("2026-05-29", "0", "0", "0", false),     // filtered
                agPoint("2026-05-30", "31", "1710000", "1.71", true));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString())).thenReturn(raw);

        SilverHistoryResponse resp = service.getSilverHistory("ALL", "TRY");

        assertThat(resp.getSymbol()).isEqualTo("BIST/GUMUS-REF");
        assertThat(resp.getCurrency()).isEqualTo("TRY");
        assertThat(resp.isOfficial()).isTrue();
        assertThat(resp.getPoints()).hasSize(2);
        SilverHistoryPoint first = resp.getPoints().get(0);
        assertThat(first.getClose()).isEqualByComparingTo("1.70");
        assertThat(first.getOpen()).isEqualByComparingTo("1.70"); // prev null → own
        assertThat(first.getCloseTryKg()).isEqualByComparingTo("1700000");
        assertThat(resp.getPoints().get(1).getOpen()).isEqualByComparingTo("1.70"); // prev carried
    }

    @Test
    @DisplayName("history 5Y/USD → AG metal-ref endpoint, usd ons fields")
    void getSilverHistory_fiveYearUsd_metalRef() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of(agPoint("2026-05-30", "31.5", "1710000", "1.71", true)));

        SilverHistoryResponse resp = service.getSilverHistory("5Y", "USD");

        assertThat(resp.getCurrency()).isEqualTo("USD");
        SilverHistoryPoint pt = resp.getPoints().get(0);
        assertThat(pt.getClose()).isEqualByComparingTo("31.5");
        assertThat(pt.getWeightedAverageUsdOns()).isEqualByComparingTo("31.5");
    }

    @Test
    @DisplayName("history 1M/TRY hybrid: vst present for a date wins over AG; AG-only date fills gap")
    void getSilverHistory_tryHybrid_vstAndAg() {
        // vst has 05-29 (OHLC). AG has 05-29 (ignored) and 05-30 (gap fill)
        BistPreciousMetalPoint vst29 = tryPoint("2026-05-29", "34", "34.10", "33.50", "34.50", true);
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(vst29));
        List<BistMetalDailyPoint> ag = List.of(
                agPoint("2026-05-29", "34", "34100", "34.10", true),
                agPoint("2026-05-30", "35", "35000", "35.00", true));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString())).thenReturn(ag);

        SilverHistoryResponse resp = service.getSilverHistory("1M", "TRY");

        assertThat(resp.getSymbol()).isEqualTo("BIST/GUMUS");
        assertThat(resp.getCurrency()).isEqualTo("TRY");
        assertThat(resp.getPoints()).hasSize(2);
        // sorted ascending by date (TreeSet)
        SilverHistoryPoint d29 = resp.getPoints().get(0);
        assertThat(d29.getDate()).isEqualTo("2026-05-29");
        assertThat(d29.getClose()).isEqualByComparingTo("34.10"); // from vst
        assertThat(d29.getHigh()).isEqualByComparingTo("34.50");  // real OHLC from vst
        assertThat(d29.getVolume()).isEqualTo(5000L);             // 5.0 kg * 1000
        SilverHistoryPoint d30 = resp.getPoints().get(1);
        assertThat(d30.getDate()).isEqualTo("2026-05-30");
        assertThat(d30.getClose()).isEqualByComparingTo("35.00"); // AG gap fill
        assertThat(d30.getHigh()).isEqualByComparingTo("35.00");  // high=low=close for AG
        assertThat(d30.getLow()).isEqualByComparingTo("35.00");
        assertThat(d30.getOpen()).isEqualByComparingTo("34.10");  // prevClose from vst 05-29
    }

    @Test
    @DisplayName("history 1M/TRY: a date with neither vst nor ag valid is skipped")
    void getSilverHistory_tryHybrid_skipsEmptyDate() {
        // vst has invalid 05-29 (filtered out of vstByDate) → date absent
        BistPreciousMetalPoint vstInvalid = tryPoint("2026-05-29", "34", "34.10", "33.50", "34.50", false);
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(vstInvalid));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of(agPoint("2026-05-30", "35", "35000", "35.00", true)));

        SilverHistoryResponse resp = service.getSilverHistory("1M", "TRY");

        // only 05-30 from AG survives; 05-29 not in either valid map
        assertThat(resp.getPoints()).hasSize(1);
        assertThat(resp.getPoints().get(0).getDate()).isEqualTo("2026-05-30");
    }

    @Test
    @DisplayName("history 3M/USD → BIST ons endpoint, OHLC + synthetic open, invalid filtered")
    void getSilverHistory_usd() {
        BistPreciousMetalPoint o1 = usdPoint("2026-05-29", "32.0", "32.4", "31.7", "32.1", true);
        BistPreciousMetalPoint o2 = usdPoint("2026-05-30", "32.6", "33.0", "32.3", "32.7", true);
        BistPreciousMetalPoint invalid = usdPoint("2026-05-31", "0", "0", "0", "0", false);
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of(o1, o2, invalid));

        SilverHistoryResponse resp = service.getSilverHistory("3M", "USD");

        assertThat(resp.getSymbol()).isEqualTo("BIST/GUMUS-ONS");
        assertThat(resp.getCurrency()).isEqualTo("USD");
        assertThat(resp.getPoints()).hasSize(2);
        SilverHistoryPoint pt = resp.getPoints().get(0);
        assertThat(pt.getClose()).isEqualByComparingTo("32.0");
        assertThat(pt.getHigh()).isEqualByComparingTo("32.4");
        assertThat(pt.getOpen()).isEqualByComparingTo("32.0"); // first → own
        assertThat(pt.getVolume()).isEqualTo(2000L);           // 2.0 kg * 1000
        assertThat(resp.getPoints().get(1).getOpen()).isEqualByComparingTo("32.0"); // prev close
    }

    @Test
    @DisplayName("history null range defaults to 1M; null currency defaults to TRY")
    void getSilverHistory_nullDefaults() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.TRY_KG), anyString(), anyString()))
                .thenReturn(List.of(tryPoint("2026-05-30", "35", "35.1", "34.5", "35.5", true)));
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        SilverHistoryResponse resp = service.getSilverHistory(null, null);

        assertThat(resp.getRange()).isEqualTo("1M");
        assertThat(resp.getCurrency()).isEqualTo("TRY");
        assertThat(resp.getPoints()).hasSize(1);
    }

    @Test
    @DisplayName("history lowercase 'usd' routes to USD path")
    void getSilverHistory_lowercaseUsd() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of(usdPoint("2026-05-30", "32.0", "32.4", "31.7", "32.1", true)));

        SilverHistoryResponse resp = service.getSilverHistory("3M", "usd");

        assertThat(resp.getSymbol()).isEqualTo("BIST/GUMUS-ONS");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  range → BIST dates
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("range ALL → 2011-01-01 start (metal-ref path)")
    void rangeToBistDates_all() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        service.getSilverHistory("ALL", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(metalClient).fetchMetalPrices(eq(PreciousMetalType.SILVER), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo("2011-01-01");
    }

    @Test
    @DisplayName("range 1Y → one year ago (USD path)")
    void rangeToBistDates_oneYear() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of(usdPoint("2026-05-30", "32.0", "32.4", "31.7", "32.1", true)));

        service.getSilverHistory("1Y", "USD");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(bistClient).fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.USD_ONS), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusYears(1).format(FMT));
    }

    @Test
    @DisplayName("range 1D → 5 days ago (USD path)")
    void rangeToBistDates_oneDay() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of(usdPoint("2026-05-30", "32.0", "32.4", "31.7", "32.1", true)));

        service.getSilverHistory("1D", "USD");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(bistClient).fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.USD_ONS), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusDays(5).format(FMT));
    }

    @Test
    @DisplayName("range 1W → 10 days ago (USD path)")
    void rangeToBistDates_oneWeek() {
        when(bistClient.fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.USD_ONS), anyString(), anyString()))
                .thenReturn(List.of(usdPoint("2026-05-30", "32.0", "32.4", "31.7", "32.1", true)));

        service.getSilverHistory("1W", "USD");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(bistClient).fetchHistory(eq(PreciousMetalType.SILVER), eq(PriceUnit.USD_ONS), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusDays(10).format(FMT));
    }

    @Test
    @DisplayName("range 5Y → five years ago (metal-ref path)")
    void rangeToBistDates_fiveYears() {
        when(metalClient.fetchMetalPrices(eq(PreciousMetalType.SILVER), anyString(), anyString()))
                .thenReturn(List.of());

        service.getSilverHistory("5Y", "TRY");

        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        verify(metalClient).fetchMetalPrices(eq(PreciousMetalType.SILVER), start.capture(), anyString());
        assertThat(start.getValue()).isEqualTo(LocalDate.now(ISTANBUL).minusYears(5).format(FMT));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  cache evict
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("evictSilverCaches runs without touching any client")
    void evictSilverCaches_noInteraction() {
        service.evictSilverCaches();
        verify(bistClient, never()).fetchLatestValidPoint(any(), any());
        verify(metalClient, never()).fetchMetalPrices(any(), anyString(), anyString());
    }
}
