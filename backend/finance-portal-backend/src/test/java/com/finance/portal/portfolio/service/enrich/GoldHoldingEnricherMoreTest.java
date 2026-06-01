package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Ek kapsama: {@link GoldHoldingEnricher} için mevcut {@code GoldHoldingEnricherTest}'in
 * dokunmadığı dallar — sikke/ziynet türevleri (YARIM/TAM/ZIYNET/CUMHUR/ATA), 22AYAR,
 * sembol normalizasyonu (GOLD: prefix varyantları + null/blank), legacy gramTl/updatedAt
 * fallback'leri ve GOLD/ons history için marketFxService USD/TRY fallback yolu.
 */
@ExtendWith(MockitoExtension.class)
class GoldHoldingEnricherMoreTest {

    @Mock GoldMarketService goldMarketService;
    @Mock MarketFxService marketFxService;

    private GoldHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new GoldHoldingEnricher(goldMarketService, marketFxService);
    }

    private static GoldSpotResponse minimalSpot() {
        GoldSpotResponse s = new GoldSpotResponse();
        s.setLastUpdated("2026-05-26T16:00:00");
        s.setUsdTry(new BigDecimal("35.5"));
        s.setChangePercent(new BigDecimal("1.0"));
        return s;
    }

    private static PortfolioHoldingResponse holding(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    private static GoldHistoryResponse history(BigDecimal... closes) {
        List<GoldHistoryPoint> pts = new ArrayList<>();
        for (BigDecimal c : closes) {
            GoldHistoryPoint p = new GoldHistoryPoint();
            p.setClose(c);
            pts.add(p);
        }
        GoldHistoryResponse h = new GoldHistoryResponse();
        h.setPoints(pts);
        return h;
    }

    // ---- additional per-symbol branches ------------------------------------

    @Test
    @DisplayName("YARIM: fiyat = halfGoldTry; isim 'Yarım Altın'")
    void yarim_corePath() {
        GoldSpotResponse spot = minimalSpot();
        spot.setHalfGoldTry(new BigDecimal("10000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("YARIM", new BigDecimal("3"), new BigDecimal("28000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("10000");
        assertThat(h.getMarketValue()).isEqualByComparingTo("30000.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("2000.0000");
        assertThat(h.getName()).isEqualTo("Yarım Altın");
        // change derived from gram changePercent: 10000 × 1% / 100 = 100.0000
        assertThat(h.getChange()).isEqualByComparingTo("100.0000");
    }

    @Test
    @DisplayName("TAM: fiyat = ziynetGoldTry; isim 'Tam Altın'")
    void tam_corePath() {
        GoldSpotResponse spot = minimalSpot();
        spot.setZiynetGoldTry(new BigDecimal("20000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("TAM", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("20000");
        assertThat(h.getName()).isEqualTo("Tam Altın");
    }

    @Test
    @DisplayName("ZIYNET: ziynetGoldTry; isim 'Tam Altın' (TAM ile aynı görünür ad)")
    void ziynet_corePath() {
        GoldSpotResponse spot = minimalSpot();
        spot.setZiynetGoldTry(new BigDecimal("21000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("ZIYNET", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("21000");
        assertThat(h.getName()).isEqualTo("Tam Altın");
    }

    @Test
    @DisplayName("CUMHUR: republicGoldTry; isim 'Cumhuriyet Altını'")
    void cumhur_corePath() {
        GoldSpotResponse spot = minimalSpot();
        spot.setRepublicGoldTry(new BigDecimal("22000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("CUMHUR", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("22000");
        assertThat(h.getName()).isEqualTo("Cumhuriyet Altını");
    }

    @Test
    @DisplayName("ATA: republicGoldTry; isim 'Cumhuriyet Altını' (CUMHUR alias)")
    void ata_corePath() {
        GoldSpotResponse spot = minimalSpot();
        spot.setRepublicGoldTry(new BigDecimal("23000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("ATA", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("23000");
        assertThat(h.getName()).isEqualTo("Cumhuriyet Altını");
    }

    @Test
    @DisplayName("22AYAR: twentyTwoKBraceletTry; change/changePercent spot'tan")
    void ayar22_corePath() {
        GoldSpotResponse spot = minimalSpot();
        spot.setTwentyTwoKBraceletTry(new BigDecimal("2800"));
        spot.setChange(new BigDecimal("12"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("22AYAR", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("2800");
        assertThat(h.getName()).isEqualTo("22 Ayar Bilezik");
        assertThat(h.getChange()).isEqualByComparingTo("12");
        assertThat(h.getChangePercent()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("22AYAR: twentyTwoKBraceletTry yoksa ayar22Tl legacy fallback")
    void ayar22_legacyFallback() {
        GoldSpotResponse spot = minimalSpot();
        spot.setAyar22Tl(new BigDecimal("2750"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("22AYAR", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("2750");
    }

    @Test
    @DisplayName("GRAM: gramGoldTry yoksa gramTl legacy fallback")
    void gram_legacyFallback() {
        GoldSpotResponse spot = minimalSpot();
        spot.setGramTl(new BigDecimal("2999"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("GRAM", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("2999");
    }

    // ---- asOf parsing: updatedAt fallback ----------------------------------

    @Test
    @DisplayName("asOf: lastUpdated null ise updatedAt fallback'ı parse edilir")
    void asOf_fallsBackToUpdatedAt() {
        GoldSpotResponse spot = minimalSpot();
        spot.setLastUpdated(null);
        spot.setUpdatedAt("2026-05-27T10:30:00");
        spot.setGramGoldTry(new BigDecimal("3000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("GRAM", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getAsOf()).isNotNull();
        assertThat(h.getAsOf().toString()).isEqualTo("2026-05-27T10:30");
    }

    // ---- symbol normalization ----------------------------------------------

    @Test
    @DisplayName("normalize: 'GOLD:GRAM_TRY' → GRAM dalı")
    void normalize_goldPrefixGram() {
        GoldSpotResponse spot = minimalSpot();
        spot.setGramGoldTry(new BigDecimal("3000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("GOLD:GRAM_TRY", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getName()).isEqualTo("Gram Altın");
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("normalize: 'GOLD:ONS_USD' → GOLD (ons) dalı")
    void normalize_goldPrefixOns() {
        GoldSpotResponse spot = minimalSpot();
        spot.setOnsTry(new BigDecimal("110000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("GOLD:ONS_USD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getName()).isEqualTo("Altın (Ons)");
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("110000");
    }

    @Test
    @DisplayName("normalize: 'GOLD:CEYREK' → CEYREK dalı (default suffix passthrough)")
    void normalize_goldPrefixCeyrek() {
        GoldSpotResponse spot = minimalSpot();
        spot.setQuarterGoldTry(new BigDecimal("5000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("gold:ceyrek", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getName()).isEqualTo("Çeyrek Altın");
    }

    @Test
    @DisplayName("normalizeGoldSymbol: null → \"\", blank → \"\"")
    void normalize_nullAndBlank() {
        assertThat(GoldHoldingEnricher.normalizeGoldSymbol(null)).isEmpty();
        assertThat(GoldHoldingEnricher.normalizeGoldSymbol("   ")).isEmpty();
        assertThat(GoldHoldingEnricher.normalizeGoldSymbol(" gram ")).isEqualTo("GRAM");
        assertThat(GoldHoldingEnricher.normalizeGoldSymbol("GOLD:")).isEqualTo("GOLD");
    }

    @Test
    @DisplayName("goldHoldingDisplayName: bilinmeyen kod → 'Altın (X)'")
    void displayName_unknownFallback() {
        assertThat(GoldHoldingEnricher.goldHoldingDisplayName("XAU")).isEqualTo("Altın (XAU)");
        assertThat(GoldHoldingEnricher.goldHoldingDisplayName("AYAR14")).isEqualTo("14 Ayar Bilezik");
        assertThat(GoldHoldingEnricher.goldHoldingDisplayName("AYAR22")).isEqualTo("22 Ayar Bilezik");
    }

    @Test
    @DisplayName("null sembol → boş normalize → default switch → UnsupportedOperationException")
    void nullSymbol_throwsUnsupported() {
        when(goldMarketService.getSpotGold()).thenReturn(minimalSpot());

        PortfolioHoldingResponse h = holding(null, BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported gold symbol");
    }

    // ---- GOLD history: usdTry fallback via marketFxService -----------------

    @Test
    @DisplayName("GOLD history: spot.usdTry null → marketFxService.getTcmbLatestRates fallback ile USD serisi TL'ye çevrilir")
    void goldHistory_usdTryFallbackFromFx() {
        GoldSpotResponse spot = minimalSpot();
        spot.setOnsTry(new BigDecimal("100000"));
        spot.setUsdTry(null); // GOLD branch price uses onsTry; history needs fallback usdTry
        when(goldMarketService.getSpotGold()).thenReturn(spot);

        // 20 closes all = 3000 USD; usdTry fallback = 40 → 120000.00 TL
        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal("3000");
        }
        when(goldMarketService.getGoldHistory("1Y", "USD")).thenReturn(history(vals));

        FxRateItem usd = new FxRateItem("USD", new BigDecimal("39.5"), new BigDecimal("40"), 1);
        FxLatestRates rates = new FxLatestRates("tcmb", "live", "TRY", "now", List.of(usd));
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(rates);

        PortfolioHoldingResponse h = holding("GOLD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // 3000 × 40 = 120000.00
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("120000.00");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("120000.00");
        assertThat(h.getMa20()).isEqualByComparingTo("120000.00");
    }

    @Test
    @DisplayName("GOLD history: usdTry hem spot hem FX'te yok → seri boş, 52w doldurulmaz")
    void goldHistory_noUsdTry_skips() {
        GoldSpotResponse spot = minimalSpot();
        spot.setOnsTry(new BigDecimal("100000"));
        spot.setUsdTry(null);
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        lenient().when(goldMarketService.getGoldHistory(any(), any())).thenReturn(history(new BigDecimal("3000")));
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(null);

        PortfolioHoldingResponse h = holding("GOLD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getFiftyTwoWeekHigh()).isNull();
        assertThat(h.getFiftyTwoWeekLow()).isNull();
    }

    @Test
    @DisplayName("GOLD history: FX servisi exception fırlatırsa fail-soft (52w null, çekirdek alanlar korunur)")
    void goldHistory_fxThrows_failSoft() {
        GoldSpotResponse spot = minimalSpot();
        spot.setOnsTry(new BigDecimal("100000"));
        spot.setUsdTry(null);
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        lenient().when(goldMarketService.getGoldHistory(any(), any())).thenReturn(history(new BigDecimal("3000")));
        when(marketFxService.getTcmbLatestRates("USD")).thenThrow(new RuntimeException("tcmb down"));

        PortfolioHoldingResponse h = holding("GOLD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100000");
        assertThat(h.getFiftyTwoWeekHigh()).isNull();
    }
}
