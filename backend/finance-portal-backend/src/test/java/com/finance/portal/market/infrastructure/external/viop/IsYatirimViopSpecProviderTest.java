package com.finance.portal.market.infrastructure.external.viop;

import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.AssetClass;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.SettlementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IsYatirimViopSpecProvider} — scrape map'inden {@link ViopContractSpec} çözümü.
 * Client mock'lanır; provider'ın dönüşüm + assetClass tahmini + marginAmount taşıması test edilir.
 */
class IsYatirimViopSpecProviderTest {

    private IsYatirimViopSpecClient client;
    private IsYatirimViopSpecProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(IsYatirimViopSpecClient.class);
        provider = new IsYatirimViopSpecProvider(client);
    }

    private IsYatirimViopSpecClient.IsYatirimViopSpec raw(String title, String mult, String margin,
                                                         String ccy, boolean phys) {
        return new IsYatirimViopSpecClient.IsYatirimViopSpec(
                title, new BigDecimal(mult), margin == null ? null : new BigDecimal(margin), ccy, phys);
    }

    @Test
    @DisplayName("scrape'ten USDTRY → FX spec + marginAmount taşınır")
    void resolvesFxWithMarginAmount() {
        when(client.fetchSpecs()).thenReturn(Map.of(
                "F_USDTRY0626", raw("F_USDTRY0626", "1000", "5362.674", "TRY", false)));

        Optional<ViopContractSpec> out = provider.resolve("F_USDTRY0626");

        assertThat(out).isPresent();
        ViopContractSpec spec = out.get();
        assertThat(spec.code()).isEqualTo("USDTRY");
        assertThat(spec.assetClass()).isEqualTo(AssetClass.FX);
        assertThat(spec.multiplier()).isEqualByComparingTo("1000");
        assertThat(spec.marginAmount()).isEqualByComparingTo("5362.674"); // gerçek teminat tutarı taşındı
        assertThat(spec.settlementType()).isEqualTo(SettlementType.CASH);  // physical=false
        assertThat(spec.currency()).isEqualTo("TRY");
    }

    @Test
    @DisplayName("scrape'ten AKBNK → SINGLE_STOCK + PHYSICAL")
    void resolvesStockPhysical() {
        when(client.fetchSpecs()).thenReturn(Map.of(
                "F_AKBNK0626", raw("F_AKBNK0626", "100", "996.45", "TRY", true)));

        ViopContractSpec spec = provider.resolve("F_AKBNK0626").orElseThrow();
        assertThat(spec.code()).isEqualTo("AKBNK");
        assertThat(spec.assetClass()).isEqualTo(AssetClass.SINGLE_STOCK);
        assertThat(spec.multiplier()).isEqualByComparingTo("100");
        assertThat(spec.marginAmount()).isEqualByComparingTo("996.45");
        assertThat(spec.settlementType()).isEqualTo(SettlementType.PHYSICAL);
    }

    @Test
    @DisplayName("map'te olmayan sembol → empty (registry fallback'e gider)")
    void notInMap() {
        when(client.fetchSpecs()).thenReturn(Map.of(
                "F_AKBNK0626", raw("F_AKBNK0626", "100", "996.45", "TRY", true)));
        assertThat(provider.resolve("F_THYAO0626")).isEmpty();
    }

    @Test
    @DisplayName("boş map (scrape çökmüş) → empty")
    void emptyMap() {
        when(client.fetchSpecs()).thenReturn(Map.of());
        assertThat(provider.resolve("F_USDTRY0626")).isEmpty();
    }

    @Test
    @DisplayName("null/boş sembol → empty")
    void blankSymbol() {
        assertThat(provider.resolve(null)).isEmpty();
        assertThat(provider.resolve("")).isEmpty();
    }
}
