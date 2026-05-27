package com.finance.portal.portfolio.service.enrich;

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
import static org.mockito.Mockito.when;

/**
 * Characterization: {@link GoldHoldingEnricher} ile eski
 * {@code PortfolioHoldingMarketEnricher.enrichGoldHolding(...)} davranışı bire bir aynı.
 *
 * <p>BigPara spot zaten 8+ farklı kontrat tipi (GRAM/CEYREK/YARIM/TAM/CUMHUR/ATA/14AYAR/22AYAR/GOLD)
 * için ayrı TL alanlar getirir; biz sadece doğru alanı seçtiğimizi, mv/pl scale'lerini ve change
 * türevini koruduğumuzu pinliyoruz.
 */
@ExtendWith(MockitoExtension.class)
class GoldHoldingEnricherTest {

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

    // ---- per-symbol branches ----------------------------------------------

    @Test
    @DisplayName("GRAM: fiyat = gramGoldTry; change/changePercent spot'tan gelir")
    void gram_corePath() {
        GoldSpotResponse spot = minimalSpot();
        spot.setGramGoldTry(new BigDecimal("3000"));
        spot.setGramHighTry(new BigDecimal("3050"));
        spot.setGramLowTry(new BigDecimal("2950"));
        spot.setChange(new BigDecimal("15"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("gram", new BigDecimal("5"), new BigDecimal("14000"));
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("3000");
        assertThat(h.getMarketValue()).isEqualByComparingTo("15000.0000");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("1000.0000");
        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("Gram Altın");
        assertThat(h.getChange()).isEqualByComparingTo("15");
        assertThat(h.getDayHigh()).isEqualByComparingTo("3050");
        assertThat(h.getDayLow()).isEqualByComparingTo("2950");
    }

    @Test
    @DisplayName("GOLD (ons): onsTry doğrudan; high/low/change USD × usdTry ile TL'ye çevrilir")
    void ons_corePath() {
        GoldSpotResponse spot = minimalSpot();
        spot.setOnsTry(new BigDecimal("110000"));
        spot.setOnsHigh(new BigDecimal("3200"));     // USD
        spot.setOnsLow(new BigDecimal("3000"));
        spot.setOnsChange(new BigDecimal("20"));
        spot.setOnsChangePercent(new BigDecimal("0.6"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("GOLD", new BigDecimal("0.1"), BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("110000");
        // dayHigh = 3200 × 35.5 = 113600.00
        assertThat(h.getDayHigh()).isEqualByComparingTo("113600.00");
        assertThat(h.getDayLow()).isEqualByComparingTo("106500.00");
        // change = 20 × 35.5 = 710.00
        assertThat(h.getChange()).isEqualByComparingTo("710.00");
        assertThat(h.getChangePercent()).isEqualByComparingTo("0.6");
        assertThat(h.getName()).isEqualTo("Altın (Ons)");
    }

    @Test
    @DisplayName("GOLD (ons): onsTry yok ama onsUsd + usdTry var → hesaplanır")
    void ons_priceFromUsd() {
        GoldSpotResponse spot = minimalSpot();
        spot.setOnsUsd(new BigDecimal("3000"));
        spot.setUsdTry(new BigDecimal("35.5"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("GOLD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // 3000 × 35.5 = 106500.00
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("106500.00");
    }

    @Test
    @DisplayName("CEYREK: fiyat = quarterGoldTry; change spot.changePercent'ten türetilir")
    void ceyrek_changeDerivedFromGramPercent() {
        GoldSpotResponse spot = minimalSpot();
        spot.setQuarterGoldTry(new BigDecimal("5000"));
        // change null, sadece changePercent var
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("CEYREK", new BigDecimal("2"), BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("5000");
        // change türevi: 5000 × 1.0% / 100 = 50.0000
        assertThat(h.getChange()).isEqualByComparingTo("50.0000");
        assertThat(h.getName()).isEqualTo("Çeyrek Altın");
    }

    @Test
    @DisplayName("14AYAR: fourteenKBraceletTry yoksa ayar14Tl fallback'ı")
    void ayar14_fallbackToLegacyField() {
        GoldSpotResponse spot = minimalSpot();
        spot.setAyar14Tl(new BigDecimal("1800"));   // legacy fallback
        when(goldMarketService.getSpotGold()).thenReturn(spot);
        when(goldMarketService.getGoldHistory(any(), any())).thenReturn(null);

        PortfolioHoldingResponse h = holding("14AYAR", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("1800");
        assertThat(h.getName()).isEqualTo("14 Ayar Bilezik");
    }

    @Test
    @DisplayName("Bilinmeyen sembol → UnsupportedOperationException")
    void unknownSymbol_throws() {
        when(goldMarketService.getSpotGold()).thenReturn(minimalSpot());

        PortfolioHoldingResponse h = holding("FOO", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported gold symbol");
    }

    // ---- fail-soft branches -----------------------------------------------

    @Test
    @DisplayName("CEYREK: fiyat null → IllegalStateException (GOLD'a özel fail-soft kıymet türü için yok)")
    void ceyrek_priceNull_throws() {
        when(goldMarketService.getSpotGold()).thenReturn(minimalSpot());   // quarterGoldTry null

        PortfolioHoldingResponse h = holding("CEYREK", BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> enricher.enrich(h))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Gold price unavailable");
    }

    @Test
    @DisplayName("GOLD: fiyat null → fail-soft (name + asOf + volume set, mv yok)")
    void ons_priceNull_failSoft() {
        GoldSpotResponse spot = minimalSpot();    // onsTry + onsUsd null
        spot.setQuantityKg(new BigDecimal("123"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);

        PortfolioHoldingResponse h = holding("GOLD", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getCurrency()).isEqualTo("TRY");
        assertThat(h.getName()).isEqualTo("Altın (Ons)");
        assertThat(h.getVolume()).isEqualTo(123L);
        assertThat(h.getMarketValue()).isNull();
    }

    // ---- history / 52w / MA ------------------------------------------------

    @Test
    @DisplayName("GRAM: history varsa 52w min/max + MA")
    void gram_history52wAndMa() {
        GoldSpotResponse spot = minimalSpot();
        spot.setGramGoldTry(new BigDecimal("3000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);

        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal(i + 1);   // 1..20
        }
        when(goldMarketService.getGoldHistory("1Y", "TRY")).thenReturn(history(vals));

        PortfolioHoldingResponse h = holding("GRAM", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1");
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("20");
        assertThat(h.getMa20()).isEqualByComparingTo("10.5");
    }

    @Test
    @DisplayName("CEYREK: history teorik çarpan (1.754 × 0.9166) ile ölçeklenir")
    void ceyrek_historyAppliesTheoryFactor() {
        GoldSpotResponse spot = minimalSpot();
        spot.setQuarterGoldTry(new BigDecimal("5000"));
        when(goldMarketService.getSpotGold()).thenReturn(spot);

        // 20 gün, hep 1000 — çarpan: 1.754 × 0.9166 = 1.607...
        BigDecimal[] vals = new BigDecimal[20];
        for (int i = 0; i < 20; i++) {
            vals[i] = new BigDecimal("1000");
        }
        when(goldMarketService.getGoldHistory("1Y", "TRY")).thenReturn(history(vals));

        PortfolioHoldingResponse h = holding("CEYREK", BigDecimal.ONE, BigDecimal.ZERO);
        enricher.enrich(h);

        // 1000 × 1.754 × 0.9166 = 1607.7... → setScale(2) = 1607.71
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("1607.72");
        assertThat(h.getFiftyTwoWeekLow()).isEqualByComparingTo("1607.72");
    }
}
