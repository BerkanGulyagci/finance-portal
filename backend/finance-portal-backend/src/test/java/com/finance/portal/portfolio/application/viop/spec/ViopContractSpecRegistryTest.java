package com.finance.portal.portfolio.application.viop.spec;

import com.finance.portal.market.application.viop.ViopIndexCodeMapper;
import com.finance.portal.portfolio.infrastructure.viop.config.ViopContractSpecProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ViopContractSpecRegistry} testleri — YAML yerine elle kurulan {@link ViopContractSpecProperties}
 * ve mock {@link ViopIndexCodeMapper}. {@code load()} elle çağrılır (Spring @PostConstruct yok).
 */
class ViopContractSpecRegistryTest {

    private ViopIndexCodeMapper indexCodeMapper;
    private ViopContractSpecRegistry registry;

    @BeforeEach
    void setUp() {
        indexCodeMapper = mock(ViopIndexCodeMapper.class);
        // Çoğu test kanonik-ad yolunu kullanmaz; varsayılan: çevrilemedi.
        lenient().when(indexCodeMapper.toIsYatirimEndeksCode(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        ViopContractSpecProperties props = new ViopContractSpecProperties();
        props.setContractSpecs(List.of(
                entry("AKBNK", "SINGLE_STOCK", "100", "0.146", "TRY", "PHYSICAL"),
                entry("USDTRY", "FX", "1000", "0.10", "TRY", "CASH"),
                entry(null, "FX", "1", "0.1", "TRY", "CASH")  // code=null → atlanmalı
        ));
        registry = new ViopContractSpecRegistry(props, indexCodeMapper);
        registry.load();
    }

    private static ViopContractSpecProperties.Entry entry(String code, String assetClass,
                                                          String multiplier, String marginRate,
                                                          String currency, String settlementType) {
        ViopContractSpecProperties.Entry e = new ViopContractSpecProperties.Entry();
        e.setCode(code);
        e.setAssetClass(assetClass);
        e.setMultiplier(new BigDecimal(multiplier));
        e.setMarginRate(new BigDecimal(marginRate));
        e.setCurrency(currency);
        e.setSettlementType(settlementType);
        return e;
    }

    @Test
    @DisplayName("load: null code'lu entry atlanır, geçerli iki spec yüklenir")
    void load_skipsNullCode() {
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("resolveByCode: case-insensitive, trim ile lookup")
    void resolveByCode_caseInsensitive() {
        Optional<ViopContractSpec> spec = registry.resolveByCode("  akbnk  ");
        assertThat(spec).isPresent();
        assertThat(spec.get().code()).isEqualTo("AKBNK");
        assertThat(spec.get().multiplier()).isEqualByComparingTo("100");
        assertThat(spec.get().marginRate()).isEqualByComparingTo("0.146");
        assertThat(spec.get().assetClass()).isEqualTo(ViopContractSpec.AssetClass.SINGLE_STOCK);
    }

    @Test
    @DisplayName("resolveByCode: null veya bilinmeyen kod → empty")
    void resolveByCode_unknown() {
        assertThat(registry.resolveByCode(null)).isEmpty();
        assertThat(registry.resolveByCode("ZZZZ")).isEmpty();
    }

    @Test
    @DisplayName("resolveBySymbol: F_AKBNK0626 → AKBNK spec'i bulur")
    void resolveBySymbol_standardSymbol() {
        Optional<ViopContractSpec> spec = registry.resolveBySymbol("F_AKBNK0626");
        assertThat(spec).isPresent();
        assertThat(spec.get().code()).isEqualTo("AKBNK");
    }

    @Test
    @DisplayName("resolveBySymbol: null/blank → empty")
    void resolveBySymbol_nullBlank() {
        assertThat(registry.resolveBySymbol(null)).isEmpty();
        assertThat(registry.resolveBySymbol("   ")).isEmpty();
    }

    @Test
    @DisplayName("resolveBySymbol: parse edilse de map'te yoksa → empty")
    void resolveBySymbol_parsedButMissing() {
        // F_THYAO0626 → THYAO; map'te yok
        assertThat(registry.resolveBySymbol("F_THYAO0626")).isEmpty();
    }

    @Test
    @DisplayName("resolveBySymbol: kanonik ad → indexCodeMapper ile F_ formatına çevrilir")
    void resolveBySymbol_canonicalName() {
        when(indexCodeMapper.toIsYatirimEndeksCode("USDTRY (30 HAZ 26) VADELI"))
                .thenReturn(Optional.of("F_USDTRY0626"));

        Optional<ViopContractSpec> spec = registry.resolveBySymbol("USDTRY (30 HAZ 26) VADELI");
        assertThat(spec).isPresent();
        assertThat(spec.get().code()).isEqualTo("USDTRY");
        assertThat(spec.get().assetClass()).isEqualTo(ViopContractSpec.AssetClass.FX);
    }

    @Test
    @DisplayName("resolveOrFallback: bilinen sembol → gerçek spec")
    void resolveOrFallback_known() {
        ViopContractSpec spec = registry.resolveOrFallback("F_AKBNK0626");
        assertThat(spec.code()).isEqualTo("AKBNK");
        assertThat(spec.multiplier()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("resolveOrFallback: bilinmeyen sembol → fallback (parsedCode, multiplier=1, margin=0.15)")
    void resolveOrFallback_unknownParsed() {
        ViopContractSpec spec = registry.resolveOrFallback("F_THYAO0626");
        assertThat(spec.code()).isEqualTo("THYAO");
        assertThat(spec.multiplier()).isEqualByComparingTo("1");
        assertThat(spec.marginRate()).isEqualByComparingTo("0.15");
        assertThat(spec.assetClass()).isEqualTo(ViopContractSpec.AssetClass.SINGLE_STOCK);
    }

    @Test
    @DisplayName("resolveOrFallback: parse edilemeyen sembol → fallback code=sembolün kendisi")
    void resolveOrFallback_unparseable() {
        ViopContractSpec spec = registry.resolveOrFallback("GARBAGE");
        assertThat(spec.code()).isEqualTo("GARBAGE");
        assertThat(spec.multiplier()).isEqualByComparingTo("1");
        assertThat(spec.marginRate()).isEqualByComparingTo("0.15");
    }
}
