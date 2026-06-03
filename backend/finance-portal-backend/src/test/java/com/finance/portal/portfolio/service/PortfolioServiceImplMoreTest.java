package com.finance.portal.portfolio.service;

import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.application.port.PortfolioCachePort;
import com.finance.portal.portfolio.application.port.PortfolioPersistencePort;
import com.finance.portal.portfolio.application.port.WatchlistMarketEnrichmentPort;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.presentation.dto.AddTransactionRequest;
import com.finance.portal.portfolio.presentation.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.UpdatePortfolioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Complementary branch-coverage tests for {@link PortfolioServiceImpl} that target arms NOT
 * exercised by {@link PortfolioServiceImplTest}:
 *   - getPortfolioById cache-miss with EMPTY holdings (skip enrich-fan-out branch)
 *   - addTransaction null transactionDate (skip future-date + bond-issue checks)
 *   - FUTURE SELL with null/blank direction → "LONG" pool arm of validateDirection ternary
 *   - FUTURE insufficient-quantity → positive-qty arm + " (LONG havuzu)" suffix
 *   - BOND BUY where issue-date lookup yields empty (eurobond null/blank/garbage + EVDS null)
 *   - finalizePortfolioTotals with NON-empty closed positions + null-converted holding values
 *   - normalizeSymbol FUTURE fallback (normalizeStoredFutureSymbol → null)
 *   - getUserPortfolios cache-miss: empty-holdings continue + bond-symbol null/blank/non-bond skips
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplMoreTest {

    @Mock private PortfolioPersistencePort portfolioPersistence;
    @Mock private PortfolioCachePort portfolioCache;
    @Mock private ViopService viopService;
    @Mock private WatchlistMarketEnrichmentPort watchlistMarketEnrichment;
    @Mock private PortfolioHoldingsBuilder holdingsBuilder;
    @Mock private HoldingMarketEnrichmentPort holdingMarketEnrichment;
    @Mock private PortfolioPerformanceService portfolioPerformanceService;
    @Mock private CentralBusinessLogService centralBusinessLogService;
    @Mock private PortfolioRealReturnEnricher realReturnEnricher;
    @Mock private PortfolioCurrencyConverter currencyConverter;
    @Mock private PortfolioWhatIfService whatIfService;
    @Mock private EurobondService eurobondService;
    @Mock private EvdsBondService evdsBondService;
    @Mock private ViopContractSpecRegistry viopSpecRegistry;

    private PortfolioServiceImpl service;

    private static final String USER = "user-1";

    @BeforeEach
    void setUp() {
        service = new PortfolioServiceImpl(
                portfolioPersistence, portfolioCache, viopService, watchlistMarketEnrichment,
                holdingsBuilder, holdingMarketEnrichment, portfolioPerformanceService,
                centralBusinessLogService, realReturnEnricher, currencyConverter, whatIfService,
                eurobondService, evdsBondService, viopSpecRegistry);

        // Sensible defaults — overridden per-test where a different shape is required.
        lenient().when(holdingsBuilder.buildWithClosed(any()))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(new ArrayList<>(), List.of()));
        lenient().when(holdingsBuilder.buildWithClosed(any(), eq(true)))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(new ArrayList<>(), List.of()));
        lenient().when(currencyConverter.toTry(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Portfolio portfolio(PortfolioType type) {
        Portfolio p = new Portfolio();
        p.setId(UUID.randomUUID());
        p.setUserId(USER);
        p.setName("My Portfolio");
        p.setDescription("desc");
        p.setCurrency("TRY");
        p.setPortfolioType(type);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private PortfolioTransaction tx(AssetType assetType, TransactionType type, String symbol,
                                    String qty, String price, String direction) {
        PortfolioTransaction t = new PortfolioTransaction();
        t.setId(UUID.randomUUID());
        t.setSymbol(symbol);
        t.setAssetType(assetType);
        t.setTransactionType(type);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setCommission(BigDecimal.ZERO);
        t.setTransactionDate(LocalDateTime.now().minusDays(10));
        t.setCreatedAt(LocalDateTime.now().minusDays(10));
        t.setDirection(direction);
        return t;
    }

    private AddTransactionRequest txRequest(AssetType assetType, TransactionType type,
                                            String symbol, String qty, String price) {
        AddTransactionRequest r = new AddTransactionRequest();
        r.setSymbol(symbol);
        r.setAssetType(assetType);
        r.setTransactionType(type);
        r.setQuantity(new BigDecimal(qty));
        r.setPrice(new BigDecimal(price));
        r.setTransactionDate(LocalDateTime.now().minusDays(1));
        return r;
    }

    // ── getPortfolioById: cache-miss with EMPTY holdings (enrich fan-out skipped) ──

    @Test
    @DisplayName("getPortfolioById: cache miss + empty holdings skips enrich loop, still caches")
    void getPortfolioById_cacheMissEmptyHoldings() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioCache.getPortfolioDetail(USER, p.getId())).thenReturn(Optional.empty());
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        // default builder stub yields EMPTY holdings → the `holdings != null && !isEmpty()` arm is FALSE

        PortfolioResponse resp = service.getPortfolioById(USER, p.getId());

        assertThat(resp.getHoldings()).isEmpty();
        // no holding to enrich
        verify(holdingMarketEnrichment, never()).enrich(any());
        // realReturnEnricher: once inside finalizePortfolioTotals + once after live re-enrich
        verify(realReturnEnricher, times(2)).apply(resp);
        verify(portfolioCache).putPortfolioDetail(USER, p.getId(), resp);
    }

    // ── addTransaction: null transactionDate → both date guards skipped ──────

    @Test
    @DisplayName("addTransaction: null transactionDate skips future-date guard, persists")
    void addTransaction_nullDateStock() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        AddTransactionRequest req = txRequest(AssetType.STOCK, TransactionType.BUY, "garan", "10", "5");
        req.setTransactionDate(null);

        service.addTransaction(USER, p.getId(), req);

        assertThat(p.getTransactions()).hasSize(1);
        assertThat(p.getTransactions().get(0).getTransactionDate()).isNull();
        verify(portfolioCache).evictListAndDetail(USER, p.getId());
    }

    @Test
    @DisplayName("addTransaction: BOND BUY with null transactionDate skips issue-date check")
    void addTransaction_bondNullDateSkipsIssueCheck() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        // null date → the `transactionDate != null` arm of the BOND-issue compound condition is FALSE,
        // so neither eurobondService nor evdsBondService is consulted for the issue date.
        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setCategory(com.finance.portal.market.application.bond.evds.model.BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(bond);

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "100", "73");
        req.setTransactionDate(null);

        service.addTransaction(USER, p.getId(), req);

        assertThat(p.getTransactions()).hasSize(1);
        verify(eurobondService, never()).currentIsins();
    }

    // ── addTransaction: FUTURE SELL with null direction → "LONG" pool arm ────

    @Test
    @DisplayName("addTransaction: FUTURE SELL without direction defaults validation pool to LONG")
    void addTransaction_futureSellNullDirectionLongPool() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        // open LONG position of 5
        p.getTransactions().add(tx(AssetType.FUTURE, TransactionType.BUY, "F_AKBNK0626", "5", "10", "LONG"));
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        AddTransactionRequest req = txRequest(AssetType.FUTURE, TransactionType.SELL, "F_AKBNK0626", "3", "12");
        // no direction set → validateDirection ternary resolves to "LONG"
        req.setDirection(null);

        service.addTransaction(USER, p.getId(), req);

        PortfolioTransaction added = p.getTransactions().get(p.getTransactions().size() - 1);
        // FUTURE tx persisted with default LONG direction
        assertThat(added.getDirection()).isEqualTo("LONG");
    }

    @Test
    @DisplayName("addTransaction: FUTURE SELL blank direction → LONG pool, insufficient qty shows (LONG havuzu)")
    void addTransaction_futureSellInsufficientShowsPoolSuffix() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        // open LONG position of only 2
        p.getTransactions().add(tx(AssetType.FUTURE, TransactionType.BUY, "F_AKBNK0626", "2", "10", "LONG"));
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));

        AddTransactionRequest req = txRequest(AssetType.FUTURE, TransactionType.SELL, "F_AKBNK0626", "5", "12");
        req.setDirection("  "); // blank → "LONG"

        assertThatThrownBy(() -> service.addTransaction(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Yetersiz miktar")
                .hasMessageContaining("(LONG havuzu)");
    }

    // ── addTransaction: BOND issue-date lookup yields empty → validation skipped ──

    @Test
    @DisplayName("addTransaction: eurobond detail null → issue date empty, BUY proceeds")
    void addTransaction_eurobondDetailNull() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        when(eurobondService.currentIsins()).thenReturn(List.of("XS1234567890"));
        when(eurobondService.detail("XS1234567890")).thenReturn(null); // → parseEurobondIssueDate empty

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "XS1234567890", "100", "99");
        req.setTransactionDate(LocalDateTime.of(2000, 1, 1, 0, 0));

        service.addTransaction(USER, p.getId(), req);

        assertThat(p.getTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("addTransaction: eurobond blank issueDate → empty, BUY proceeds")
    void addTransaction_eurobondBlankIssueDate() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        when(eurobondService.currentIsins()).thenReturn(List.of("XS1234567890"));
        EurobondDetail d = new EurobondDetail();
        d.setIssueDate("   "); // blank → parseEurobondIssueDate empty
        when(eurobondService.detail("XS1234567890")).thenReturn(d);

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "XS1234567890", "100", "99");
        req.setTransactionDate(LocalDateTime.of(2000, 1, 1, 0, 0));

        service.addTransaction(USER, p.getId(), req);

        assertThat(p.getTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("addTransaction: eurobond unparseable issueDate → catch → empty, BUY proceeds")
    void addTransaction_eurobondUnparseableIssueDate() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        when(eurobondService.currentIsins()).thenReturn(List.of("XS1234567890"));
        EurobondDetail d = new EurobondDetail();
        d.setIssueDate("not-a-date"); // LocalDate.parse throws → caught → empty
        when(eurobondService.detail("XS1234567890")).thenReturn(d);

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "XS1234567890", "100", "99");
        req.setTransactionDate(LocalDateTime.of(2000, 1, 1, 0, 0));

        service.addTransaction(USER, p.getId(), req);

        assertThat(p.getTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("addTransaction: EVDS bond with null issueDate → empty, BUY proceeds (non-eurobond path)")
    void addTransaction_evdsNullIssueDate() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        when(eurobondService.currentIsins()).thenReturn(List.of()); // not eurobond → EVDS path
        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIssueDate(null); // parseEvdsBondIssueDate → empty
        bond.setCategory(com.finance.portal.market.application.bond.evds.model.BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(bond);

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "100", "73");
        req.setTransactionDate(LocalDateTime.of(2000, 1, 1, 0, 0));

        service.addTransaction(USER, p.getId(), req);

        assertThat(p.getTransactions()).hasSize(1);
    }

    @Test
    @DisplayName("addTransaction: EVDS bond detail lookup throws → empty, BUY proceeds")
    void addTransaction_evdsLookupThrows() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        when(eurobondService.currentIsins()).thenReturn(List.of());
        // both the issue-date validation AND toTransactionResponse parScale lookup go through here
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17"))
                .thenThrow(new RuntimeException("evds down"));

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "100", "73");
        req.setTransactionDate(LocalDateTime.of(2000, 1, 1, 0, 0));

        service.addTransaction(USER, p.getId(), req);

        assertThat(p.getTransactions()).hasSize(1);
    }

    // ── finalizePortfolioTotals: non-empty closed positions + null-converted holding values ──

    @Test
    @DisplayName("finalizePortfolioTotals: sums closed realized + filters null-converted holding values")
    void totals_closedRealizedAndNullFilter() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        // Open holding whose converter returns null for some fields → filtered out (Objects::nonNull arm)
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("THYAO.IS");
        h.setAssetType(AssetType.STOCK);
        h.setCurrency("TRY");
        h.setTotalCost(new BigDecimal("100"));
        h.setMarketValue(null);            // null source
        h.setProfitLoss(new BigDecimal("20"));
        h.setRealizedGainLoss(new BigDecimal("5"));

        // a fully-closed position contributing realized 30 TRY
        var closed = new PortfolioHoldingsBuilder.ClosedPositionRealized(
                "GARAN", AssetType.STOCK, new BigDecimal("30"), "TRY");
        when(holdingsBuilder.buildWithClosed(any()))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(
                        new ArrayList<>(List.of(h)), List.of(closed)));

        // converter: pass-through for non-null, but return null when source value is null
        when(currencyConverter.toTry(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName(p.getName());
        req.setDescription(p.getDescription());
        PortfolioResponse resp = service.updatePortfolio(USER, p.getId(), req);

        assertThat(resp.getTotalCost()).isEqualByComparingTo("100");
        // marketValue holding source was null → contributes nothing → total 0
        assertThat(resp.getTotalMarketValue()).isEqualByComparingTo("0");
        assertThat(resp.getTotalProfitLoss()).isEqualByComparingTo("20");
        // openRealized 5 + closedRealized 30 = 35
        assertThat(resp.getTotalRealizedProfitLoss()).isEqualByComparingTo("35");
    }

    @Test
    @DisplayName("finalizePortfolioTotals: currencyConverter returning null for ALL keeps totals at zero")
    void totals_allNullConversions() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("AAPL");
        h.setAssetType(AssetType.STOCK);
        h.setCurrency("USD");
        h.setTotalCost(new BigDecimal("100"));
        h.setMarketValue(new BigDecimal("150"));
        h.setProfitLoss(new BigDecimal("50"));
        h.setRealizedGainLoss(new BigDecimal("10"));
        when(holdingsBuilder.buildWithClosed(any()))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(
                        new ArrayList<>(List.of(h)), List.of()));

        // simulate missing FX → every conversion is null, exercising the filter(Objects::nonNull) arm everywhere
        when(currencyConverter.toTry(any(), any())).thenReturn(null);

        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName(p.getName());
        req.setDescription(p.getDescription());
        PortfolioResponse resp = service.updatePortfolio(USER, p.getId(), req);

        assertThat(resp.getTotalCost()).isEqualByComparingTo("0");
        assertThat(resp.getTotalMarketValue()).isEqualByComparingTo("0");
        assertThat(resp.getTotalProfitLoss()).isEqualByComparingTo("0");
        assertThat(resp.getTotalRealizedProfitLoss()).isEqualByComparingTo("0");
    }

    // ── normalizeSymbol: FUTURE fallback when normalizeStoredFutureSymbol → null ──

    @Test
    @DisplayName("addWatchlistItem: FUTURE symbol that normalizes to empty uses raw uppercase fallback")
    void addWatchlistItem_futureNormalizeFallback() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        // "." → ViopService.normalizeStoredFutureSymbol returns null → fallback symbol.trim().toUpperCase(ROOT)
        when(portfolioPersistence.existsWatchlistItem(eq(p.getId()), any(), eq(AssetType.FUTURE)))
                .thenReturn(false);
        when(portfolioPersistence.saveWatchlistItem(any())).thenAnswer(inv -> {
            var it = (com.finance.portal.portfolio.domain.WatchlistItem) inv.getArgument(0);
            it.setId(UUID.randomUUID());
            return it;
        });

        AddWatchlistItemRequest req = new AddWatchlistItemRequest();
        req.setSymbol(" . ");
        req.setAssetType(AssetType.FUTURE);

        service.addWatchlistItem(USER, p.getId(), req);

        ArgumentCaptor<com.finance.portal.portfolio.domain.WatchlistItem> cap =
                ArgumentCaptor.forClass(com.finance.portal.portfolio.domain.WatchlistItem.class);
        verify(portfolioPersistence).saveWatchlistItem(cap.capture());
        // fallback path: trimmed + uppercased raw (no Akbank canonicalization possible)
        assertThat(cap.getValue().getSymbol()).isEqualTo(".");
    }

    // ── getUserPortfolios: cache-miss empty-holdings continue + bond null/blank/non-bond skips ──

    @Test
    @DisplayName("getUserPortfolios: cache miss, empty holdings hits the per-portfolio continue branch")
    void getUserPortfolios_emptyHoldingsContinue() {
        when(portfolioCache.getPortfolioList(USER)).thenReturn(Optional.empty());
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        // a STOCK tx → no bond pre-warm; builder yields EMPTY holdings → fan-out `continue`
        p.getTransactions().add(tx(AssetType.STOCK, TransactionType.BUY, "GARAN", "10", "5", null));
        when(portfolioPersistence.findByUserId(USER)).thenReturn(List.of(p));

        List<PortfolioResponse> resp = service.getUserPortfolios(USER);

        assertThat(resp).hasSize(1);
        // no holding → enrichHolding never invoked
        verify(holdingsBuilder, never()).enrichHolding(any());
        verify(portfolioCache).putPortfolioList(eq(USER), any());
    }

    @Test
    @DisplayName("getUserPortfolios: bond pre-warm skips null/blank-symbol and non-bond transactions")
    void getUserPortfolios_bondSymbolFilters() {
        when(portfolioCache.getPortfolioList(USER)).thenReturn(Optional.empty());
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        // BOND with null symbol → skipped (symbol != null arm false)
        p.getTransactions().add(tx(AssetType.BOND, TransactionType.BUY, null, "1", "1", null));
        // BOND with blank symbol → skipped (!isBlank arm false)
        p.getTransactions().add(tx(AssetType.BOND, TransactionType.BUY, "   ", "1", "1", null));
        // non-BOND → skipped (assetType arm false)
        p.getTransactions().add(tx(AssetType.STOCK, TransactionType.BUY, "GARAN", "1", "1", null));
        // BOND with valid symbol → the only one pre-warmed
        p.getTransactions().add(tx(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "100", "73", null));
        when(portfolioPersistence.findByUserId(USER)).thenReturn(List.of(p));

        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("TRT240227T17");
        h.setAssetType(AssetType.BOND);
        when(holdingsBuilder.buildWithClosed(any(), eq(true)))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(
                        new ArrayList<>(List.of(h)), List.of()));
        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setCategory(com.finance.portal.market.application.bond.evds.model.BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(bond);

        List<PortfolioResponse> resp = service.getUserPortfolios(USER);

        assertThat(resp).hasSize(1);
        // the valid bond symbol drives the pre-warm loop (null/blank/non-bond skipped from the
        // distinct-symbol set; toTransactionResponse may still touch per-tx but that is orthogonal)
        verify(evdsBondService, atLeastOnce()).getEvdsBondDetail("TRT240227T17");
        verify(holdingsBuilder).enrichHolding(h);
        verify(portfolioCache).putPortfolioList(eq(USER), any());
    }

    @Test
    @DisplayName("getUserPortfolios: cache miss, enrichHolding throwing is swallowed")
    void getUserPortfolios_enrichThrowsSwallowed() {
        when(portfolioCache.getPortfolioList(USER)).thenReturn(Optional.empty());
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        p.getTransactions().add(tx(AssetType.STOCK, TransactionType.BUY, "GARAN", "10", "5", null));
        when(portfolioPersistence.findByUserId(USER)).thenReturn(List.of(p));

        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("GARAN");
        h.setAssetType(AssetType.STOCK);
        when(holdingsBuilder.buildWithClosed(any(), eq(true)))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(
                        new ArrayList<>(List.of(h)), List.of()));
        org.mockito.Mockito.doThrow(new RuntimeException("enrich boom"))
                .when(holdingsBuilder).enrichHolding(h);

        List<PortfolioResponse> resp = service.getUserPortfolios(USER);

        assertThat(resp).hasSize(1);
        verify(portfolioCache).putPortfolioList(eq(USER), any());
    }
}
