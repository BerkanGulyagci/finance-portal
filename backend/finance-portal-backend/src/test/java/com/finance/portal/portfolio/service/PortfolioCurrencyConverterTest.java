package com.finance.portal.portfolio.service;

import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PortfolioCurrencyConverterTest {

    private MarketFxService fxService;
    private PortfolioCurrencyConverter converter;

    @BeforeEach
    void setUp() {
        fxService = mock(MarketFxService.class);
        converter = new PortfolioCurrencyConverter(fxService);
    }

    // ------------------------------ rateToTry ------------------------------

    @Test
    @DisplayName("rateToTry: TRY → 1 (FX servisine gitmez)")
    void rateToTry_try_returnsOne() {
        assertThat(converter.rateToTry("TRY")).isEqualByComparingTo("1");
        assertThat(converter.rateToTry("try")).isEqualByComparingTo("1");
        verifyNoInteractions(fxService);
    }

    @Test
    @DisplayName("rateToTry: 'TL' alias → 1")
    void rateToTry_tlAlias_returnsOne() {
        assertThat(converter.rateToTry("TL")).isEqualByComparingTo("1");
        assertThat(converter.rateToTry("tl")).isEqualByComparingTo("1");
        verifyNoInteractions(fxService);
    }

    @Test
    @DisplayName("rateToTry: null / boş → 1 (TL varsayımı)")
    void rateToTry_nullBlank_returnsOne() {
        assertThat(converter.rateToTry(null)).isEqualByComparingTo("1");
        assertThat(converter.rateToTry("")).isEqualByComparingTo("1");
        assertThat(converter.rateToTry("   ")).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("rateToTry: USD → TCMB satış kurunu döner")
    void rateToTry_usd_returnsSellPrice() {
        FxRateItem usd = new FxRateItem("USD", new BigDecimal("45.20"), new BigDecimal("45.27"), 1);
        when(fxService.getTcmbLatestRates("USD"))
                .thenReturn(new FxLatestRates(null, null, null, null, List.of(usd)));

        assertThat(converter.rateToTry("USD")).isEqualByComparingTo("45.27");
    }

    @Test
    @DisplayName("rateToTry: satış yoksa alış kuruna düşer")
    void rateToTry_sellNull_fallsBackToBuy() {
        FxRateItem usd = new FxRateItem("USD", new BigDecimal("45.20"), null, 1);
        when(fxService.getTcmbLatestRates("USD"))
                .thenReturn(new FxLatestRates(null, null, null, null, List.of(usd)));

        assertThat(converter.rateToTry("USD")).isEqualByComparingTo("45.20");
    }

    @Test
    @DisplayName("rateToTry: JPY (unit=100) → kur unit'e bölünür")
    void rateToTry_unitGreaterThanOne_dividesByUnit() {
        // 100 JPY = 28 TRY → 1 JPY = 0.28 TRY
        FxRateItem jpy = new FxRateItem("JPY", new BigDecimal("27.50"), new BigDecimal("28.00"), 100);
        when(fxService.getTcmbLatestRates("JPY"))
                .thenReturn(new FxLatestRates(null, null, null, null, List.of(jpy)));

        BigDecimal rate = converter.rateToTry("JPY");

        assertThat(rate).isEqualByComparingTo("0.28");
    }

    @Test
    @DisplayName("rateToTry: rates listesinde sembol yok → null")
    void rateToTry_symbolNotFound_returnsNull() {
        FxRateItem usd = new FxRateItem("USD", new BigDecimal("45.20"), new BigDecimal("45.27"), 1);
        when(fxService.getTcmbLatestRates("EUR"))
                .thenReturn(new FxLatestRates(null, null, null, null, List.of(usd)));

        assertThat(converter.rateToTry("EUR")).isNull();
    }

    @Test
    @DisplayName("rateToTry: FX servisi null → null")
    void rateToTry_fxServiceNull_returnsNull() {
        when(fxService.getTcmbLatestRates(anyString())).thenReturn(null);

        assertThat(converter.rateToTry("USD")).isNull();
    }

    @Test
    @DisplayName("rateToTry: FX servisi hata fırlatırsa → null (en iyi çaba)")
    void rateToTry_fxServiceThrows_returnsNull() {
        when(fxService.getTcmbLatestRates(anyString()))
                .thenThrow(new RuntimeException("upstream down"));

        assertThat(converter.rateToTry("USD")).isNull();
    }

    // ------------------------------ toTry ------------------------------

    @Test
    @DisplayName("toTry: null tutar → null")
    void toTry_nullAmount_returnsNull() {
        assertThat(converter.toTry(null, "USD")).isNull();
        verifyNoInteractions(fxService);
    }

    @Test
    @DisplayName("toTry: TRY → tutarı aynen döner")
    void toTry_tryAmount_returnsUnchanged() {
        BigDecimal amount = new BigDecimal("1000.50");

        assertThat(converter.toTry(amount, "TRY")).isEqualByComparingTo("1000.50");
        verifyNoInteractions(fxService);
    }

    @Test
    @DisplayName("toTry: USD → tutar × kur")
    void toTry_usdAmount_multipliesByRate() {
        FxRateItem usd = new FxRateItem("USD", new BigDecimal("45.20"), new BigDecimal("45.27"), 1);
        when(fxService.getTcmbLatestRates("USD"))
                .thenReturn(new FxLatestRates(null, null, null, null, List.of(usd)));

        BigDecimal out = converter.toTry(new BigDecimal("100"), "USD");

        // 100 × 45.27 = 4527
        assertThat(out).isEqualByComparingTo("4527.00");
    }

    @Test
    @DisplayName("toTry: kur bulunamazsa tutar olduğu gibi döner (en iyi çaba)")
    void toTry_unknownCurrency_returnsAmountUnchanged() {
        when(fxService.getTcmbLatestRates("XXX")).thenReturn(null);

        BigDecimal amount = new BigDecimal("42");
        assertThat(converter.toTry(amount, "XXX")).isEqualByComparingTo("42");
    }
}
