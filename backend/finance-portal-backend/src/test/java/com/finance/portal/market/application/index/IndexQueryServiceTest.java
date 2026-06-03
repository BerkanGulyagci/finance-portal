package com.finance.portal.market.application.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.stock.StockSymbolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tests for {@link IndexQueryService}.
 *
 * <p>{@code LastKnownGoodCache} is mocked as a pass-through: {@code resilient(...)} simply invokes the
 * supplied fetch {@link Supplier} so the real {@code fetchIndices()} branches execute. The service is
 * injected as its own {@code self} proxy so {@code getIndex()}'s self-invocation path runs in-process.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndexQueryServiceTest {

    @Mock
    private StockQueryService stockQueryService;
    @Mock
    private StockSymbolProvider stockSymbolProvider;
    @Mock
    private LastKnownGoodCache lkg;

    private IndexQueryService service;

    @BeforeEach
    void setUp() {
        service = new IndexQueryService(stockQueryService, stockSymbolProvider, lkg, null);
        // self-proxy points at the real instance (no Spring proxy in a unit test).
        service = new IndexQueryService(stockQueryService, stockSymbolProvider, lkg, service);

        // LKG pass-through: run the actual fetch supplier so fetchIndices()'s branches execute.
        when(lkg.resilient(eq("market.indices.list"), any(Duration.class),
                any(TypeReference.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    private static StockSummary summary(String symbol, BigDecimal price) {
        StockSummary s = new StockSummary();
        s.setSymbol(symbol);
        s.setName(symbol);
        s.setCurrency("TRY");
        s.setPrice(price);
        s.setChange(new BigDecimal("1.50"));
        s.setChangePercent(new BigDecimal("0.75"));
        s.setDayHigh(new BigDecimal("105.00"));
        s.setDayLow(new BigDecimal("95.00"));
        s.setVolume(0L); // Yahoo returns volume=0 for indices — must not affect mapping.
        return s;
    }

    // ── getIndices() / fetchIndices() branches ──────────────────────────────────

    @Test
    void getIndices_mapsLiveSummaries_andCopiesAllFields() {
        // One real catalog symbol with full data.
        StockSummary live = summary("XU100.IS", new BigDecimal("9000.12"));
        when(stockQueryService.getSummariesFor(anyList())).thenReturn(List.of(live));

        List<IndexSummary> out = service.getIndices();

        assertThat(out).isNotEmpty();
        IndexSummary xu100 = out.stream()
                .filter(i -> "XU100".equals(i.getCode())).findFirst().orElseThrow();
        assertThat(xu100.getSymbol()).isEqualTo("XU100.IS");
        assertThat(xu100.getName()).isEqualTo("BIST 100");
        assertThat(xu100.getCategory()).isEqualTo("Ana");
        assertThat(xu100.getPrice()).isEqualByComparingTo("9000.12");
        assertThat(xu100.getChange()).isEqualByComparingTo("1.50");
        assertThat(xu100.getChangePercent()).isEqualByComparingTo("0.75");
        assertThat(xu100.getDayHigh()).isEqualByComparingTo("105.00");
        assertThat(xu100.getDayLow()).isEqualByComparingTo("95.00");
        assertThat(xu100.getCurrency()).isEqualTo("TRY");
        // Only one symbol had data; every other catalog entry was skipped (s == null arm).
        assertThat(out).hasSize(1);
    }

    @Test
    void getIndices_filtersOutNullSymbolAndNullPrice_thenSkipsMissing() {
        // s.getSymbol()==null  → filtered (left arm of &&).
        StockSummary nullSymbol = summary(null, new BigDecimal("1"));
        // s.getPrice()==null    → filtered (right arm of &&).
        StockSummary nullPrice = summary("XU030.IS", null);
        // valid → passes filter.
        StockSummary valid = summary("XU050.IS", new BigDecimal("8000"));
        when(stockQueryService.getSummariesFor(anyList()))
                .thenReturn(List.of(nullSymbol, nullPrice, valid));

        List<IndexSummary> out = service.getIndices();

        // Only XU050 survived; XU030 (null price) and all others were skipped.
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getCode()).isEqualTo("XU050");
    }

    @Test
    void getIndices_allMissing_returnsEmpty() {
        when(stockQueryService.getSummariesFor(anyList())).thenReturn(List.of());

        List<IndexSummary> out = service.getIndices();

        assertThat(out).isEmpty();
    }

    // ── getIndex() branches ─────────────────────────────────────────────────────

    @Test
    void getIndex_unknownCode_throwsNotFound() {
        assertThatThrownBy(() -> service.getIndex("NOPE"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Index not found: NOPE");
        verifyNoInteractions(stockQueryService);
    }

    @Test
    void getIndex_foundInCachedList_returnsCached_noSingleFetch() {
        StockSummary live = summary("XU100.IS", new BigDecimal("9100"));
        when(stockQueryService.getSummariesFor(anyList())).thenReturn(List.of(live));

        IndexSummary result = service.getIndex("xu100"); // case-insensitive match

        assertThat(result.getCode()).isEqualTo("XU100");
        assertThat(result.getPrice()).isEqualByComparingTo("9100");
        // Cached branch taken → no single-symbol Yahoo fetch.
        verify(stockQueryService, never()).getStockSummary(any());
    }

    @Test
    void getIndex_notInCachedList_fallsBackToSingleFetch() {
        // List has only XU050 live → XU030 absent from cached list → single-fetch arm.
        StockSummary other = summary("XU050.IS", new BigDecimal("8000"));
        when(stockQueryService.getSummariesFor(anyList())).thenReturn(List.of(other));
        StockSummary single = summary("XU030.IS", new BigDecimal("7777.77"));
        when(stockQueryService.getStockSummary("XU030.IS")).thenReturn(single);

        IndexSummary result = service.getIndex("XU030");

        assertThat(result.getCode()).isEqualTo("XU030");
        assertThat(result.getName()).isEqualTo("BIST 30");
        assertThat(result.getPrice()).isEqualByComparingTo("7777.77");
        verify(stockQueryService).getStockSummary("XU030.IS");
    }

    // ── getConstituents() branches ──────────────────────────────────────────────

    @Test
    void getConstituents_unknownCode_returnsEmpty() {
        List<StockSummary> out = service.getConstituents("ZZZ");

        assertThat(out).isEmpty();
        verify(stockQueryService, never()).getSummariesFor(anyList());
    }

    @Test
    void getConstituents_sizeIndex_usesLiveProvider() {
        when(stockSymbolProvider.getBist30Symbols())
                .thenReturn(List.of("GARAN.IS", "AKBNK.IS"));
        StockSummary g = summary("GARAN.IS", new BigDecimal("120"));
        when(stockQueryService.getSummariesFor(List.of("GARAN.IS", "AKBNK.IS")))
                .thenReturn(List.of(g));

        List<StockSummary> out = service.getConstituents("XU030");

        assertThat(out).hasSize(1);
        verify(stockSymbolProvider).getBist30Symbols();
    }

    @Test
    void getConstituents_emptyResolvedSymbols_returnsEmpty() {
        // XU500 maps to BIST100 in the switch; force it empty to hit the symbols.isEmpty() arm.
        when(stockSymbolProvider.getBist100Symbols()).thenReturn(List.of());

        List<StockSummary> out = service.getConstituents("XU500");

        assertThat(out).isEmpty();
        verify(stockQueryService, never()).getSummariesFor(anyList());
    }

    @Test
    void getConstituents_overMaxConstituents_trimsTo110() {
        // 120 synthetic symbols → exceeds MAX_CONSTITUENTS (110) → subList branch.
        List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 120; i++) {
            many.add("SYM" + i + ".IS");
        }
        when(stockSymbolProvider.getBist100Symbols()).thenReturn(many);
        when(stockQueryService.getSummariesFor(anyList())).thenReturn(List.of());

        service.getConstituents("XU100");

        org.mockito.ArgumentCaptor<List<String>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stockQueryService).getSummariesFor(captor.capture());
        assertThat(captor.getValue()).hasSize(110);
    }

    // ── resolveConstituentSymbols() switch arms (via getConstituents) ────────────

    @Test
    void getConstituents_bist50Arm() {
        when(stockSymbolProvider.getBist50Symbols()).thenReturn(List.of("ASELS.IS"));
        when(stockQueryService.getSummariesFor(List.of("ASELS.IS"))).thenReturn(List.of());

        service.getConstituents("XU050");

        verify(stockSymbolProvider).getBist50Symbols();
    }

    @Test
    void getConstituents_bist100Arm() {
        when(stockSymbolProvider.getBist100Symbols()).thenReturn(List.of("THYAO.IS"));
        when(stockQueryService.getSummariesFor(List.of("THYAO.IS"))).thenReturn(List.of());

        service.getConstituents("XU100");

        verify(stockSymbolProvider).getBist100Symbols();
    }

    @Test
    void getConstituents_xbanaAliasArm_mapsToBist100() {
        when(stockSymbolProvider.getBist100Symbols()).thenReturn(List.of("KCHOL.IS"));
        when(stockQueryService.getSummariesFor(List.of("KCHOL.IS"))).thenReturn(List.of());

        service.getConstituents("XBANA");

        verify(stockSymbolProvider).getBist100Symbols();
    }

    @Test
    void getConstituents_curatedSectorArm_appendsIsSuffix() {
        // XBANK is a curated sector (no super-sector children) → default arm, curated path.
        when(stockQueryService.getSummariesFor(anyList())).thenReturn(List.of());

        service.getConstituents("XBANK");

        org.mockito.ArgumentCaptor<List<String>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stockQueryService).getSummariesFor(captor.capture());
        // Curated XBANK members get ".IS" appended.
        assertThat(captor.getValue()).contains("GARAN.IS", "AKBNK.IS");
        verifyNoInteractions(stockSymbolProvider);
    }

    @Test
    void getConstituents_superSectorArm_unionsChildrenWithIsSuffix() {
        // XUMAL is a super-sector → children union branch (LinkedHashSet path).
        when(stockQueryService.getSummariesFor(anyList())).thenReturn(List.of());

        service.getConstituents("XUMAL");

        org.mockito.ArgumentCaptor<List<String>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stockQueryService).getSummariesFor(captor.capture());
        List<String> syms = captor.getValue();
        // Members from child sectors, deduplicated, each with ".IS".
        assertThat(syms).contains("GARAN.IS", "TURSG.IS", "KCHOL.IS");
        assertThat(syms).doesNotHaveDuplicates();
        verifyNoInteractions(stockSymbolProvider);
    }

    @Test
    void getConstituents_unknownButValidNonSector_returnsEmptyCurated() {
        // XU100 etc. are switch-handled; pick a catalog code with no curated members
        // and no super-sector children → default arm yields empty → symbols.isEmpty() → empty.
        List<StockSummary> out = service.getConstituents("XK100");

        assertThat(out).isEmpty();
        verify(stockQueryService, never()).getSummariesFor(anyList());
    }
}
