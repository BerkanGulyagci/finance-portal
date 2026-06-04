package com.finance.portal.market.application.viop;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.market.application.viop.port.ViopContractListPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ViopService} birim testleri. Harici port ({@link ViopContractListPort}) ve
 * {@link ViopIndexCodeMapper} mock'lanır. {@code self} proxy referansı, cache çağrılarını
 * gerçekten yapabilmek için ya gerçek instance ya da ayrı bir mock olarak verilir.
 */
@ExtendWith(MockitoExtension.class)
class ViopServiceTest {

    @Mock
    private ViopContractListPort contractListPort;

    @Mock
    private ViopIndexCodeMapper indexCodeMapper;

    private ViopService service;

    /**
     * LKG pass-through: resilient(...) sadece supplier'ı çağırır (port verify sayıları korunur).
     * lenient — statik-metod testleri getAllContracts çağırmaz, bu stub onlarda kullanılmaz.
     */
    private static LastKnownGoodCache passThroughLkg() {
        LastKnownGoodCache lkg = mock(LastKnownGoodCache.class);
        lenient().when(lkg.resilient(anyString(), any(Duration.class), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
        return lkg;
    }

    @BeforeEach
    void setUp() {
        // Bu instance'taki testler self proxy kullanmaz (getContractDetail hariç,
        // o testler kendi mock self'leriyle ayrı instance kurar). self = null güvenli.
        service = new ViopService(contractListPort, null, indexCodeMapper, passThroughLkg());
    }

    private static ViopContract contract(String name) {
        ViopContract c = new ViopContract();
        c.setName(name);
        return c;
    }

    private static ViopContract fullContract(String name) {
        ViopContract c = new ViopContract();
        c.setName(name);
        c.setLastPrice("2,7000");
        c.setChangePercent("%-0,37");
        c.setHigh("1.234,56");
        c.setLow("2,5000");
        c.setOpenPositionCount("1.234");
        c.setOpenPositionChange("12");
        c.setSettlementPrice("2,6500");
        c.setPrevSettlementPrice("2,6000");
        c.setTime("18:00");
        return c;
    }

    // ──────────────────────────────────────────────────────────── getAllContracts

    @Test
    @DisplayName("getAllContracts: port listesi döner")
    void getAllContracts_returnsList() {
        when(contractListPort.fetchContracts()).thenReturn(List.of(contract("AKBNK (30 Haz 26) Vadeli FIZ.")));
        assertThat(service.getAllContracts()).hasSize(1);
    }

    @Test
    @DisplayName("getAllContracts: port null dönerse boş liste")
    void getAllContracts_nullBecomesEmpty() {
        when(contractListPort.fetchContracts()).thenReturn(null);
        assertThat(service.getAllContracts()).isEmpty();
    }

    // ─────────────────────────────────────────────── getContractsByUnderlyingAsset

    @Test
    @DisplayName("getContractsByUnderlyingAsset: prefix eşleşen kontratları döner (case-insensitive)")
    void getContractsByUnderlying_filtersByPrefix() {
        when(contractListPort.fetchContracts()).thenReturn(List.of(
                contract("ALARK (25 May 26) Vadeli FIZ."),
                contract("ALARK (30 Haz 26) Vadeli FIZ."),
                contract("AKBNK (30 Haz 26) Vadeli FIZ."),
                contract(null)
        ));
        List<ViopContract> result = service.getContractsByUnderlyingAsset("alark");
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(c -> c.getName().startsWith("ALARK"));
    }

    @Test
    @DisplayName("getContractsByUnderlyingAsset: eşleşme yoksa boş")
    void getContractsByUnderlying_noMatch() {
        when(contractListPort.fetchContracts()).thenReturn(List.of(contract("AKBNK (30 Haz 26) Vadeli FIZ.")));
        assertThat(service.getContractsByUnderlyingAsset("ZZZZZ")).isEmpty();
    }

    // ───────────────────────────────────────────── normalizeContractNameInput (static)

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("normalizeContractNameInput: null/boş → boş string")
    void normalize_nullEmpty(String in) {
        assertThat(ViopService.normalizeContractNameInput(in)).isEmpty();
    }

    @Test
    @DisplayName("normalizeContractNameInput: trim + boşluk daralt + sondaki noktaları kaldır")
    void normalize_trimsAndStripsDots() {
        assertThat(ViopService.normalizeContractNameInput("  AKBNK   (30  Haz 26)  Vadeli  FIZ...  "))
                .isEqualTo("AKBNK (30 Haz 26) Vadeli FIZ");
    }

    @Test
    @DisplayName("normalizeContractNameInput: nokta yoksa olduğu gibi")
    void normalize_noTrailingDot() {
        assertThat(ViopService.normalizeContractNameInput("AKBNK")).isEqualTo("AKBNK");
    }

    // ──────────────────────────────────────────────── parseContractMaturity (static)

    @Test
    @DisplayName("parseContractMaturity: null → empty")
    void maturity_null() {
        assertThat(ViopService.parseContractMaturity(null)).isEmpty();
    }

    @ParameterizedTest(name = "''{0}'' -> {1}-{2}-{3}")
    @CsvSource({
            "USDTRY (30 HAZ 26) VADELI, 2026, 6, 30",
            "THYAO (25 May 26) Vadeli FIZ., 2026, 5, 25",
            "X (01 OCA 27) Vadeli, 2027, 1, 1",
            "X (31 ARA 99) Vadeli, 2099, 12, 31"
    })
    @DisplayName("parseContractMaturity: Türkçe ay kısaltmalarını çözer, 2 haneli yıl 2000+")
    void maturity_parsesTurkishMonths(String name, int y, int mo, int d) {
        assertThat(ViopService.parseContractMaturity(name)).contains(LocalDate.of(y, mo, d));
    }

    @Test
    @DisplayName("parseContractMaturity: İ harfli ay (NİS) ASCII fold ile çözülür")
    void maturity_turkishDottedI() {
        assertThat(ViopService.parseContractMaturity("X (15 NİS 26) Vadeli")).contains(LocalDate.of(2026, 4, 15));
    }

    @Test
    @DisplayName("parseContractMaturity: pattern eşleşmezse empty")
    void maturity_noMatch() {
        assertThat(ViopService.parseContractMaturity("garbage no parens")).isEmpty();
    }

    @Test
    @DisplayName("parseContractMaturity: bilinmeyen ay → empty")
    void maturity_unknownMonth() {
        assertThat(ViopService.parseContractMaturity("X (15 ZZZ 26) Vadeli")).isEmpty();
    }

    @Test
    @DisplayName("parseContractMaturity: geçersiz gün (32) → empty (LocalDate.of fırlatır, yakalanır)")
    void maturity_invalidDay() {
        assertThat(ViopService.parseContractMaturity("X (32 OCA 26) Vadeli")).isEmpty();
    }

    // ─────────────────────────────────────────────── normalizeStoredFutureSymbol (static)

    @Test
    @DisplayName("normalizeStoredFutureSymbol: null → null")
    void storedSymbol_null() {
        assertThat(ViopService.normalizeStoredFutureSymbol(null)).isNull();
    }

    @Test
    @DisplayName("normalizeStoredFutureSymbol: sadece noktalar → null (normalize boş)")
    void storedSymbol_onlyDots() {
        assertThat(ViopService.normalizeStoredFutureSymbol("...")).isNull();
    }

    @Test
    @DisplayName("normalizeStoredFutureSymbol: ROOT locale ile büyük harf")
    void storedSymbol_upperCases() {
        assertThat(ViopService.normalizeStoredFutureSymbol("akbnk (30 haz 26) vadeli fiz."))
                .isEqualTo("AKBNK (30 HAZ 26) VADELI FIZ");
    }

    // ─────────────────────────────────────────────────── portfolioFutureGroupKey (static)

    @Test
    @DisplayName("portfolioFutureGroupKey: İ/I, Vadeli/VADELİ, son nokta farklarını tolere eder")
    void groupKey_tolerant() {
        String a = ViopService.portfolioFutureGroupKey("AKBNK (30 Haz 26) Vadeli FIZ.");
        String b = ViopService.portfolioFutureGroupKey("AKBNK (30 HAZ 26) VADELİ FIZ");
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("portfolioFutureGroupKey: HIZ → FIZ normalize (fiziki teslim varyantı)")
    void groupKey_hizToFiz() {
        String hiz = ViopService.portfolioFutureGroupKey("AKBNK (30 Haz 26) Vadeli HIZ");
        String fiz = ViopService.portfolioFutureGroupKey("AKBNK (30 Haz 26) Vadeli FIZ");
        assertThat(hiz).isEqualTo(fiz);
    }

    @Test
    @DisplayName("portfolioFutureGroupKey: null güvenli (boş string'e eşit)")
    void groupKey_null() {
        assertThat(ViopService.portfolioFutureGroupKey(null)).isEqualTo(ViopService.portfolioFutureGroupKey(""));
    }

    // ─────────────────────────────────────────────────────────── findMatchingContract

    @Test
    @DisplayName("findMatchingContract: null/blank → empty")
    void findMatching_blank() {
        assertThat(service.findMatchingContract(null)).isEmpty();
        assertThat(service.findMatchingContract("   ")).isEmpty();
    }

    @Test
    @DisplayName("findMatchingContract: kontrat listesi boşsa empty")
    void findMatching_emptyList() {
        when(contractListPort.fetchContracts()).thenReturn(List.of());
        assertThat(service.findMatchingContract("AKBNK (30 Haz 26) Vadeli FIZ.")).isEmpty();
    }

    @Test
    @DisplayName("findMatchingContract: tolerant exact eşleşme döner (portföy symbol yolu)")
    void findMatching_exact() {
        ViopContract c = contract("AKBNK (30 Haz 26) Vadeli FIZ.");
        when(contractListPort.fetchContracts()).thenReturn(List.of(c));
        // mapper, F_ prefix olmayan needle için çağrılmamalı (findByIsYatirimCode erken döner)
        Optional<ViopContract> r = service.findMatchingContract("AKBNK (30 HAZ 26) VADELI FIZ");
        assertThat(r).contains(c);
    }

    @Test
    @DisplayName("findMatchingContract: F_ kanonik kod → İş Yatırım reverse mapping ile bağlanır")
    void findMatching_isYatirimReverse() {
        ViopContract c = contract("AKBNK (30 Haz 26) Vadeli FIZ.");
        when(contractListPort.fetchContracts()).thenReturn(List.of(c));
        when(indexCodeMapper.toIsYatirimEndeksCode("AKBNK (30 Haz 26) Vadeli FIZ."))
                .thenReturn(Optional.of("F_AKBNK0626"));
        Optional<ViopContract> r = service.findMatchingContract("F_AKBNK0626");
        assertThat(r).contains(c);
    }

    @Test
    @DisplayName("findMatchingContract: F_ kod hiçbir kontrata map olmazsa portföy yoluna düşer ve empty")
    void findMatching_isYatirimReverseNoMatch() {
        ViopContract c = contract("AKBNK (30 Haz 26) Vadeli FIZ.");
        when(contractListPort.fetchContracts()).thenReturn(List.of(c));
        when(indexCodeMapper.toIsYatirimEndeksCode("AKBNK (30 Haz 26) Vadeli FIZ."))
                .thenReturn(Optional.of("F_THYAO0626"));
        // F_AKBNK0626 ham metni "F_AKBNK0626" — CONTRACT_NAME_PATTERN'e uymaz → portföy yolu da empty
        assertThat(service.findMatchingContract("F_AKBNK0626")).isEmpty();
    }

    @Test
    @DisplayName("findMatchingContract: underlying+gün+ay fuzzy eşleşme (yıl farklı)")
    void findMatching_fuzzyByDayMonth() {
        ViopContract c = contract("AKBNK (30 Haz 27) Vadeli FIZ.");
        when(contractListPort.fetchContracts()).thenReturn(List.of(c));
        // istenen yıl 26 ama listede 27 var → aynı gün/ay tek aday seçilir
        Optional<ViopContract> r = service.findMatchingContract("AKBNK (30 Haz 26) Vadeli FIZ.");
        assertThat(r).contains(c);
    }

    @Test
    @DisplayName("findMatchingContract: aynı gün/ay birden çok yıl → >= istenen yıl seçilir")
    void findMatching_fuzzyPicksYearGe() {
        ViopContract older = contract("AKBNK (30 Haz 25) Vadeli FIZ.");
        ViopContract target = contract("AKBNK (30 Haz 27) Vadeli FIZ.");
        when(contractListPort.fetchContracts()).thenReturn(List.of(older, target));
        Optional<ViopContract> r = service.findMatchingContract("AKBNK (30 Haz 26) Vadeli FIZ.");
        assertThat(r).contains(target);
    }

    @Test
    @DisplayName("findMatchingContract: aynı gün/ay ama hepsi istenen yıldan eski → en yenisi (son) seçilir")
    void findMatching_fuzzyAllOlder() {
        ViopContract y24 = contract("AKBNK (30 Haz 24) Vadeli FIZ.");
        ViopContract y25 = contract("AKBNK (30 Haz 25) Vadeli FIZ.");
        when(contractListPort.fetchContracts()).thenReturn(List.of(y24, y25));
        Optional<ViopContract> r = service.findMatchingContract("AKBNK (30 Haz 26) Vadeli FIZ.");
        assertThat(r).contains(y25);
    }

    @Test
    @DisplayName("findMatchingContract: parse edilemeyen needle (slot yok) → empty")
    void findMatching_unparseableNeedle() {
        ViopContract c = contract("AKBNK (30 Haz 26) Vadeli FIZ.");
        when(contractListPort.fetchContracts()).thenReturn(List.of(c));
        assertThat(service.findMatchingContract("not a contract")).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────── buildDetailDto

    @Test
    @DisplayName("buildDetailDto: null kontrat → IllegalArgumentException")
    void buildDetail_null() {
        assertThatThrownBy(() -> service.buildDetailDto(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("buildDetailDto: Türkçe sayı formatları doğru parse edilir + sembol çıkarılır")
    void buildDetail_parsesNumbers() {
        ViopContractDetail dto = service.buildDetailDto(fullContract("AKBNK (30 Haz 26) Vadeli FIZ."));
        assertThat(dto.getName()).isEqualTo("AKBNK (30 Haz 26) Vadeli FIZ.");
        assertThat(dto.getSymbol()).isEqualTo("AKBNK (30 Haz 26)");
        assertThat(dto.getLastPrice()).isEqualByComparingTo("2.7000");
        assertThat(dto.getChangePercent()).isEqualByComparingTo("-0.37");
        assertThat(dto.getHigh()).isEqualByComparingTo("1234.56");
        assertThat(dto.getLow()).isEqualByComparingTo("2.5000");
        assertThat(dto.getOpenPositionCount()).isEqualTo(1234L);
        assertThat(dto.getOpenPositionChange()).isEqualTo(12L);
        assertThat(dto.getSettlementPrice()).isEqualByComparingTo("2.6500");
        assertThat(dto.getPrevSettlementPrice()).isEqualByComparingTo("2.6000");
        assertThat(dto.getTime()).isEqualTo("18:00");
        // "AKBNK (30 Haz 26)" TradingView map'te yok → null
        assertThat(dto.getTradingViewSymbol()).isNull();
    }

    @Test
    @DisplayName("buildDetailDto: bozuk/boş sayı alanları null'a düşer (parse hatası yutulur)")
    void buildDetail_badNumbersBecomeNull() {
        ViopContract c = contract("XU030 Vadeli");
        c.setLastPrice("abc");
        c.setChangePercent("");
        c.setHigh(null);
        c.setOpenPositionCount("xyz");
        ViopContractDetail dto = service.buildDetailDto(c);
        assertThat(dto.getLastPrice()).isNull();
        assertThat(dto.getChangePercent()).isNull();
        assertThat(dto.getHigh()).isNull();
        assertThat(dto.getOpenPositionCount()).isNull();
    }

    @Test
    @DisplayName("buildDetailDto: bilinen TradingView sembolü (XU030) eklenir")
    void buildDetail_tradingViewSymbol() {
        // "XU030 Vadeli ..." → extractSymbol "vadeli" öncesini alır → "XU030"
        ViopContractDetail dto = service.buildDetailDto(contract("XU030 Vadeli FIZ."));
        assertThat(dto.getSymbol()).isEqualTo("XU030");
        assertThat(dto.getTradingViewSymbol()).isEqualTo("BIST:XU030");
    }

    @Test
    @DisplayName("buildDetailDto: 'vadeli' içermeyen ad → boşluktan önceki ilk kelime sembol olur")
    void buildDetail_symbolFirstWord() {
        ViopContractDetail dto = service.buildDetailDto(contract("USDTRY 30 Haz"));
        assertThat(dto.getSymbol()).isEqualTo("USDTRY");
        assertThat(dto.getTradingViewSymbol()).isEqualTo("FX:USDTRY");
    }

    @Test
    @DisplayName("buildDetailDto: boşluksuz tek kelime ad → tamamı sembol")
    void buildDetail_symbolSingleWord() {
        ViopContractDetail dto = service.buildDetailDto(contract("EURTRY"));
        assertThat(dto.getSymbol()).isEqualTo("EURTRY");
        assertThat(dto.getTradingViewSymbol()).isEqualTo("FX:EURTRY");
    }

    @Test
    @DisplayName("buildDetailDto: null isim → sembol boş string, TV null")
    void buildDetail_nullName() {
        ViopContractDetail dto = service.buildDetailDto(contract(null));
        assertThat(dto.getSymbol()).isEmpty();
        assertThat(dto.getTradingViewSymbol()).isNull();
    }

    // ──────────────────────────────────────────────────────────────── getContractDetail

    @Test
    @DisplayName("getContractDetail: null/boş symbol → IllegalArgumentException")
    void getDetail_blank() {
        assertThatThrownBy(() -> service.getContractDetail(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getContractDetail("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getContractDetail: self proxy'ye trimlenmiş symbol ile delege eder")
    void getDetail_delegatesToSelf() {
        ViopService selfMock = mock(ViopService.class);
        ViopService svc = new ViopService(contractListPort, selfMock, indexCodeMapper, passThroughLkg());
        ViopContractDetail expected = new ViopContractDetail();
        when(selfMock.getContractDetailCached("AKBNK (30 Haz 26) Vadeli FIZ"))
                .thenReturn(expected);
        assertThat(svc.getContractDetail("  AKBNK (30 Haz 26) Vadeli FIZ  ")).isSameAs(expected);
    }

    @Test
    @DisplayName("getContractDetail: IllegalArgumentException olduğu gibi yeniden fırlatılır")
    void getDetail_rethrowsIllegalArgument() {
        ViopService selfMock = mock(ViopService.class);
        ViopService svc = new ViopService(contractListPort, selfMock, indexCodeMapper, passThroughLkg());
        when(selfMock.getContractDetailCached("X")).thenThrow(new IllegalArgumentException("boom"));
        assertThatThrownBy(() -> svc.getContractDetail("X"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("boom");
    }

    @Test
    @DisplayName("getContractDetail: ResourceNotFoundException olduğu gibi yeniden fırlatılır")
    void getDetail_rethrowsResourceNotFound() {
        ViopService selfMock = mock(ViopService.class);
        ViopService svc = new ViopService(contractListPort, selfMock, indexCodeMapper, passThroughLkg());
        when(selfMock.getContractDetailCached("X")).thenThrow(new ResourceNotFoundException("nf"));
        assertThatThrownBy(() -> svc.getContractDetail("X"))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("nf");
    }

    @Test
    @DisplayName("getContractDetail: beklenmeyen hata → ResourceNotFoundException'a sarılır")
    void getDetail_wrapsUnexpected() {
        ViopService selfMock = mock(ViopService.class);
        ViopService svc = new ViopService(contractListPort, selfMock, indexCodeMapper, passThroughLkg());
        when(selfMock.getContractDetailCached("X")).thenThrow(new RuntimeException("upstream down"));
        assertThatThrownBy(() -> svc.getContractDetail("X"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("VIOP kontrat detayı alınamadı");
    }

    // ──────────────────────────────────────────────────────────── getContractDetailCached

    @Nested
    @DisplayName("getContractDetailCached")
    class CachedDetail {

        @Test
        @DisplayName("kontrat listesi boşsa IllegalArgumentException")
        void emptyContracts() {
            when(contractListPort.fetchContracts()).thenReturn(List.of());
            assertThatThrownBy(() -> service.getContractDetailCached("AKBNK (30 Haz 26) Vadeli FIZ."))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("yüklenemedi");
        }

        @Test
        @DisplayName("normalize sonrası boş anahtar → IllegalArgumentException")
        void emptyNeedle() {
            when(contractListPort.fetchContracts())
                    .thenReturn(List.of(contract("AKBNK (30 Haz 26) Vadeli FIZ.")));
            assertThatThrownBy(() -> service.getContractDetailCached("..."))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("boş olamaz");
        }

        @Test
        @DisplayName("eşleşme yoksa ResourceNotFoundException")
        void notFound() {
            when(contractListPort.fetchContracts())
                    .thenReturn(List.of(contract("AKBNK (30 Haz 26) Vadeli FIZ.")));
            assertThatThrownBy(() -> service.getContractDetailCached("ZZZZZ (30 Haz 26) Vadeli FIZ."))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("bulunamadı");
        }

        @Test
        @DisplayName("eşleşen kontrat → detay DTO döner")
        void found() {
            when(contractListPort.fetchContracts())
                    .thenReturn(List.of(fullContract("AKBNK (30 Haz 26) Vadeli FIZ.")));
            ViopContractDetail dto = service.getContractDetailCached("AKBNK (30 HAZ 26) VADELI FIZ");
            assertThat(dto.getName()).isEqualTo("AKBNK (30 Haz 26) Vadeli FIZ.");
            assertThat(dto.getLastPrice()).isEqualByComparingTo("2.7000");
            assertThat(dto.getChangePercent()).isEqualByComparingTo("-0.37");
        }
    }
}
