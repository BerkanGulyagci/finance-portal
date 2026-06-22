package com.finance.portal.portfolio.service;

import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceResult;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.application.port.PortfolioCachePort;
import com.finance.portal.portfolio.application.port.PortfolioPersistencePort;
import com.finance.portal.portfolio.application.port.WatchlistMarketEnrichmentPort;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import com.finance.portal.portfolio.application.whatif.PortfolioWhatIfResult;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesResult;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.domain.WatchlistItem;
import com.finance.portal.portfolio.presentation.dto.AddCouponIncomeRequest;
import com.finance.portal.portfolio.presentation.dto.AddTransactionRequest;
import com.finance.portal.portfolio.presentation.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.presentation.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.UpdatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

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

        // toPortfolioResponse needs builder + currencyConverter — set lenient defaults so the
        // many tests that reach toPortfolioResponse with an empty portfolio don't NPE.
        lenient().when(holdingsBuilder.buildWithClosed(any()))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(new java.util.ArrayList<>(), List.of()));
        lenient().when(holdingsBuilder.buildWithClosed(any(), eq(true)))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(new java.util.ArrayList<>(), List.of()));
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
        p.setCurrency("USD");
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

    // ── createPortfolio ──────────────────────────────────────────────────────

    @Test
    @DisplayName("createPortfolio: blank userId rejected")
    void createPortfolio_blankUser() {
        assertThatThrownBy(() -> service.createPortfolio(" ", new CreatePortfolioRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("createPortfolio: null request rejected")
    void createPortfolio_nullRequest() {
        assertThatThrownBy(() -> service.createPortfolio(USER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
    }

    @Test
    @DisplayName("createPortfolio: duplicate name rejected")
    void createPortfolio_duplicateName() {
        CreatePortfolioRequest req = new CreatePortfolioRequest();
        req.setName(" Dup ");
        when(portfolioPersistence.existsByUserIdAndName(USER, "Dup")).thenReturn(true);
        assertThatThrownBy(() -> service.createPortfolio(USER, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("createPortfolio: HOLDINGS default + currency default USD + cache evict")
    void createPortfolio_holdingsDefault() {
        CreatePortfolioRequest req = new CreatePortfolioRequest();
        req.setName("New");
        req.setDescription("d");
        // currency blank → default USD, portfolioType null → HOLDINGS
        when(portfolioPersistence.existsByUserIdAndName(USER, "New")).thenReturn(false);
        Portfolio saved = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.savePortfolio(any())).thenReturn(saved);

        PortfolioResponse resp = service.createPortfolio(USER, req);

        assertThat(resp).isNotNull();
        assertThat(resp.getPortfolioType()).isEqualTo(PortfolioType.HOLDINGS);
        ArgumentCaptor<Portfolio> cap = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioPersistence).savePortfolio(cap.capture());
        assertThat(cap.getValue().getCurrency()).isEqualTo("USD");
        assertThat(cap.getValue().getPortfolioType()).isEqualTo(PortfolioType.HOLDINGS);
        verify(portfolioCache).evictList(USER);
        // one business log (no watchlist extra)
        verify(centralBusinessLogService, times(1)).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), eq(USER), anyString());
    }

    @Test
    @DisplayName("createPortfolio: WATCHLIST publishes two business logs + explicit currency uppercased")
    void createPortfolio_watchlistType() {
        CreatePortfolioRequest req = new CreatePortfolioRequest();
        req.setName("WL");
        req.setCurrency("eur");
        req.setPortfolioType("WATCHLIST");
        when(portfolioPersistence.existsByUserIdAndName(USER, "WL")).thenReturn(false);
        Portfolio saved = portfolio(PortfolioType.WATCHLIST);
        when(portfolioPersistence.savePortfolio(any())).thenReturn(saved);
        when(portfolioPersistence.countWatchlistByPortfolioId(any())).thenReturn(0L);

        service.createPortfolio(USER, req);

        ArgumentCaptor<Portfolio> cap = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioPersistence).savePortfolio(cap.capture());
        assertThat(cap.getValue().getCurrency()).isEqualTo("EUR");
        verify(centralBusinessLogService, times(2)).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), eq(USER), anyString());
    }

    @Test
    @DisplayName("createPortfolio: invalid portfolioType string rejected")
    void createPortfolio_invalidType() {
        CreatePortfolioRequest req = new CreatePortfolioRequest();
        req.setName("X");
        req.setPortfolioType("BOGUS");
        when(portfolioPersistence.existsByUserIdAndName(USER, "X")).thenReturn(false);
        assertThatThrownBy(() -> service.createPortfolio(USER, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid portfolioType");
    }

    // ── addTransaction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("addTransaction: blank userId rejected")
    void addTransaction_blankUser() {
        assertThatThrownBy(() -> service.addTransaction("", UUID.randomUUID(),
                txRequest(AssetType.STOCK, TransactionType.BUY, "thyao.is", "1", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addTransaction: null request rejected")
    void addTransaction_nullRequest() {
        assertThatThrownBy(() -> service.addTransaction(USER, UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addTransaction: portfolio not found rejected")
    void addTransaction_notFound() {
        UUID pid = UUID.randomUUID();
        when(portfolioPersistence.findByIdAndUserId(pid, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addTransaction(USER, pid,
                txRequest(AssetType.STOCK, TransactionType.BUY, "thyao.is", "1", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Portfolio not found");
    }

    @Test
    @DisplayName("addTransaction: watchlist portfolio rejects transaction")
    void addTransaction_watchlistRejected() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.addTransaction(USER, p.getId(),
                txRequest(AssetType.STOCK, TransactionType.BUY, "thyao.is", "1", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Watchlist");
    }

    @Test
    @DisplayName("addTransaction: future-dated transaction rejected")
    void addTransaction_futureDate() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        AddTransactionRequest req = txRequest(AssetType.STOCK, TransactionType.BUY, "thyao.is", "1", "1");
        req.setTransactionDate(LocalDateTime.now().plusDays(5));
        assertThatThrownBy(() -> service.addTransaction(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ileri olamaz");
    }

    @Test
    @DisplayName("addTransaction: STOCK BUY persists, normalizes symbol uppercase, evicts cache")
    void addTransaction_stockBuy() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        // symbol with no locale-sensitive 'i' so uppercase is locale-independent
        service.addTransaction(USER, p.getId(),
                txRequest(AssetType.STOCK, TransactionType.BUY, "garan", "10", "5"));

        assertThat(p.getTransactions()).hasSize(1);
        assertThat(p.getTransactions().get(0).getSymbol()).isEqualTo("GARAN");
        verify(portfolioCache).evictListAndDetail(USER, p.getId());
    }

    @Test
    @DisplayName("addTransaction: CRYPTO symbol lowercased, commission defaults to 0")
    void addTransaction_cryptoLowercase() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        service.addTransaction(USER, p.getId(),
                txRequest(AssetType.CRYPTO, TransactionType.BUY, "BTC", "2", "100"));

        assertThat(p.getTransactions().get(0).getSymbol()).isEqualTo("btc");
        assertThat(p.getTransactions().get(0).getCommission()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("addTransaction: SELL beyond open quantity rejected (no position)")
    void addTransaction_sellNoPosition() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.addTransaction(USER, p.getId(),
                txRequest(AssetType.STOCK, TransactionType.SELL, "thyao.is", "5", "10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kapatılabilecek pozisyon yok");
    }

    @Test
    @DisplayName("addTransaction: SELL more than held rejected (insufficient)")
    void addTransaction_sellInsufficient() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        p.getTransactions().add(tx(AssetType.STOCK, TransactionType.BUY, "GARAN", "3", "10", null));
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.addTransaction(USER, p.getId(),
                txRequest(AssetType.STOCK, TransactionType.SELL, "garan", "5", "10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Yetersiz miktar");
    }

    @Test
    @DisplayName("addTransaction: SELL within held succeeds")
    void addTransaction_sellOk() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        p.getTransactions().add(tx(AssetType.STOCK, TransactionType.BUY, "GARAN", "10", "10", null));
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        service.addTransaction(USER, p.getId(),
                txRequest(AssetType.STOCK, TransactionType.SELL, "garan", "5", "12"));

        assertThat(p.getTransactions()).hasSize(2);
    }

    @Test
    @DisplayName("addTransaction: COUPON_INCOME açık miktara sayılmaz — kupon girilen bond satılabilir")
    void addTransaction_sellOkWithCouponIncome() {
        // Regresyon: COUPON_INCOME konvansiyonu quantity = TL kupon tutarı (price=1). Satış kontrolünde
        // bu pozisyondan DÜŞÜLMEMELİ; aksi halde 1000 BUY − 2375 kupon = negatif → yanlışlıkla
        // "kapatılabilecek pozisyon yok" hatası verir ve kuponu olan bond satılamaz.
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        p.getTransactions().add(tx(AssetType.BOND, TransactionType.BUY, "US900123AL40", "1000", "45.25", null));
        p.getTransactions().add(tx(AssetType.BOND, TransactionType.COUPON_INCOME, "US900123AL40", "2375", "1", null));
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        // Tüm 1000 nominal satılabilmeli (kupon miktarı pozisyonu negatife düşürmemeli).
        service.addTransaction(USER, p.getId(),
                txRequest(AssetType.BOND, TransactionType.SELL, "US900123AL40", "1000", "57.54"));

        assertThat(p.getTransactions()).hasSize(3);
    }

    @Test
    @DisplayName("addTransaction: FUTURE SHORT pool closed independently of LONG")
    void addTransaction_futureShortClose() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        // one SHORT open of 5
        p.getTransactions().add(tx(AssetType.FUTURE, TransactionType.BUY, "F_AKBNK0626", "5", "10", "SHORT"));
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        AddTransactionRequest req = txRequest(AssetType.FUTURE, TransactionType.SELL, "F_AKBNK0626", "3", "12");
        req.setDirection("short");
        service.addTransaction(USER, p.getId(), req);

        PortfolioTransaction added = p.getTransactions().get(p.getTransactions().size() - 1);
        assertThat(added.getDirection()).isEqualTo("SHORT");
    }

    @Test
    @DisplayName("addTransaction: FUTURE BUY default direction LONG")
    void addTransaction_futureBuyDefaultLong() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        // a not-yet-expired contract: maturity far future, parseContractMaturity returns empty
        // for symbol "F_AKBNK0626" so maturity check passes
        service.addTransaction(USER, p.getId(),
                txRequest(AssetType.FUTURE, TransactionType.BUY, "F_AKBNK0626", "1", "10"));

        PortfolioTransaction added = p.getTransactions().get(0);
        assertThat(added.getDirection()).isEqualTo("LONG");
    }

    @Test
    @DisplayName("addTransaction: FUTURE BUY on expired contract rejected")
    void addTransaction_futureExpired() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        // contract name parseable with past maturity
        AddTransactionRequest req = txRequest(AssetType.FUTURE, TransactionType.BUY,
                "USDTRY (30 OCA 20) VADELI", "1", "10");
        assertThatThrownBy(() -> service.addTransaction(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vadesi");
    }

    @Test
    @DisplayName("addTransaction: BOND BUY before eurobond issue date rejected")
    void addTransaction_bondEurobondBeforeIssue() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of("XS1234567890"));
        EurobondDetail d = new EurobondDetail();
        d.setIssueDate("7/17/2025");
        when(eurobondService.detail("XS1234567890")).thenReturn(d);

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "XS1234567890", "100", "99");
        req.setTransactionDate(LocalDateTime.of(2025, 1, 1, 0, 0)); // before issue
        assertThatThrownBy(() -> service.addTransaction(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eurobondun");
    }

    @Test
    @DisplayName("addTransaction: BOND BUY before EVDS DIBS issue date rejected")
    void addTransaction_bondEvdsBeforeIssue() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(eurobondService.currentIsins()).thenReturn(List.of()); // not eurobond → EVDS path
        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIssueDate(LocalDate.of(2025, 1, 1));
        when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(bond);

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "100", "73");
        req.setTransactionDate(LocalDateTime.of(2024, 6, 1, 0, 0)); // before issue
        assertThatThrownBy(() -> service.addTransaction(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tahvilin");
    }

    @Test
    @DisplayName("addTransaction: BOND BUY on/after issue date succeeds")
    void addTransaction_bondAfterIssueOk() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        when(eurobondService.currentIsins()).thenReturn(List.of());
        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setIssueDate(LocalDate.of(2020, 1, 1));
        // getEvdsBondDetail used by both validation and toTransactionResponse
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(bond);

        AddTransactionRequest req = txRequest(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "100", "73");
        req.setTransactionDate(LocalDateTime.of(2024, 6, 1, 0, 0));
        service.addTransaction(USER, p.getId(), req);

        assertThat(p.getTransactions()).hasSize(1);
    }

    // ── addCouponIncome ──────────────────────────────────────────────────────

    @Test
    @DisplayName("addCouponIncome: blank user / null request rejected")
    void addCouponIncome_guards() {
        assertThatThrownBy(() -> service.addCouponIncome("", UUID.randomUUID(), new AddCouponIncomeRequest()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addCouponIncome(USER, UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addCouponIncome: watchlist portfolio rejected")
    void addCouponIncome_watchlist() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        AddCouponIncomeRequest req = new AddCouponIncomeRequest();
        req.setSymbol("TRT240227T17");
        req.setAmount(new BigDecimal("100"));
        req.setPaymentDate(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.addCouponIncome(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Watchlist");
    }

    @Test
    @DisplayName("addCouponIncome: future payment date rejected")
    void addCouponIncome_futureDate() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        AddCouponIncomeRequest req = new AddCouponIncomeRequest();
        req.setSymbol("TRT240227T17");
        req.setAmount(new BigDecimal("100"));
        req.setPaymentDate(LocalDateTime.now().plusDays(2));
        assertThatThrownBy(() -> service.addCouponIncome(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ileri olamaz");
    }

    @Test
    @DisplayName("addCouponIncome: no bond holding for symbol rejected")
    void addCouponIncome_noBondHolding() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        AddCouponIncomeRequest req = new AddCouponIncomeRequest();
        req.setSymbol("TRT240227T17");
        req.setAmount(new BigDecimal("100"));
        req.setPaymentDate(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.addCouponIncome(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOND pozisyonu bulunamadı");
    }

    @Test
    @DisplayName("addCouponIncome: valid coupon persists COUPON_INCOME tx with qty=amount price=1")
    void addCouponIncome_ok() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        p.getTransactions().add(tx(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "100", "73", null));
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setCategory(BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(bond);

        AddCouponIncomeRequest req = new AddCouponIncomeRequest();
        req.setSymbol("TRT240227T17");
        req.setAmount(new BigDecimal("250"));
        req.setPaymentDate(LocalDateTime.now().minusDays(1));

        service.addCouponIncome(USER, p.getId(), req);

        PortfolioTransaction coupon = p.getTransactions().stream()
                .filter(t -> t.getTransactionType() == TransactionType.COUPON_INCOME)
                .findFirst().orElseThrow();
        assertThat(coupon.getQuantity()).isEqualByComparingTo("250");
        assertThat(coupon.getPrice()).isEqualByComparingTo("1");
        verify(portfolioCache).evictListAndDetail(USER, p.getId());
    }

    // ── getPortfolioById ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getPortfolioById: cache hit returns cached without re-enrich")
    void getPortfolioById_cacheHit() {
        UUID pid = UUID.randomUUID();
        PortfolioResponse cached = new PortfolioResponse();
        cached.setId(pid);
        when(portfolioCache.getPortfolioDetail(USER, pid)).thenReturn(Optional.of(cached));

        PortfolioResponse resp = service.getPortfolioById(USER, pid);

        assertThat(resp).isSameAs(cached);
        verify(portfolioPersistence, never()).findByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("getPortfolioById: cache miss loads, enriches holdings, writes cache")
    void getPortfolioById_cacheMiss() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioCache.getPortfolioDetail(USER, p.getId())).thenReturn(Optional.empty());
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("THYAO.IS");
        h.setAssetType(AssetType.STOCK);
        when(holdingsBuilder.buildWithClosed(any()))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(
                        new java.util.ArrayList<>(List.of(h)), List.of()));

        PortfolioResponse resp = service.getPortfolioById(USER, p.getId());

        assertThat(resp.getHoldings()).hasSize(1);
        verify(holdingMarketEnrichment).enrich(h);
        // applied once inside finalizePortfolioTotals + once after live re-enrich
        verify(realReturnEnricher, times(2)).apply(resp);
        verify(portfolioCache).putPortfolioDetail(USER, p.getId(), resp);
    }

    @Test
    @DisplayName("getPortfolioById: cache miss + holding enrich throws is swallowed")
    void getPortfolioById_enrichThrows() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioCache.getPortfolioDetail(USER, p.getId())).thenReturn(Optional.empty());
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("BAD");
        when(holdingsBuilder.buildWithClosed(any()))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(
                        new java.util.ArrayList<>(List.of(h)), List.of()));
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(holdingMarketEnrichment).enrich(h);

        PortfolioResponse resp = service.getPortfolioById(USER, p.getId());
        assertThat(resp).isNotNull();
        verify(portfolioCache).putPortfolioDetail(USER, p.getId(), resp);
    }

    @Test
    @DisplayName("getPortfolioById: not found rejected")
    void getPortfolioById_notFound() {
        UUID pid = UUID.randomUUID();
        when(portfolioCache.getPortfolioDetail(USER, pid)).thenReturn(Optional.empty());
        when(portfolioPersistence.findByIdAndUserId(pid, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPortfolioById(USER, pid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── performance / whatif passthrough ─────────────────────────────────────

    @Test
    @DisplayName("getPortfolioPerformance delegates to perf service")
    void getPortfolioPerformance() {
        UUID pid = UUID.randomUUID();
        PortfolioPerformanceResult r = org.mockito.Mockito.mock(PortfolioPerformanceResult.class);
        when(portfolioPerformanceService.getPerformance(USER, pid, "1M", "value")).thenReturn(r);
        assertThat(service.getPortfolioPerformance(USER, pid, "1M", "value")).isSameAs(r);
    }

    @Test
    @DisplayName("getPortfolioWhatIf: holdings → delegates compute")
    void getPortfolioWhatIf_ok() {
        UUID pid = UUID.randomUUID();
        PortfolioResponse cached = new PortfolioResponse();
        cached.setPortfolioType(PortfolioType.HOLDINGS);
        when(portfolioCache.getPortfolioDetail(USER, pid)).thenReturn(Optional.of(cached));
        PortfolioWhatIfResult r = org.mockito.Mockito.mock(PortfolioWhatIfResult.class);
        when(whatIfService.compute(cached)).thenReturn(r);
        assertThat(service.getPortfolioWhatIf(USER, pid)).isSameAs(r);
    }

    @Test
    @DisplayName("getPortfolioWhatIf: watchlist rejected")
    void getPortfolioWhatIf_watchlistRejected() {
        UUID pid = UUID.randomUUID();
        PortfolioResponse cached = new PortfolioResponse();
        cached.setPortfolioType(PortfolioType.WATCHLIST);
        when(portfolioCache.getPortfolioDetail(USER, pid)).thenReturn(Optional.of(cached));
        assertThatThrownBy(() -> service.getPortfolioWhatIf(USER, pid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holdings");
    }

    @Test
    @DisplayName("getPortfolioWhatIfSeries: holdings → delegates")
    void getPortfolioWhatIfSeries_ok() {
        UUID pid = UUID.randomUUID();
        PortfolioResponse cached = new PortfolioResponse();
        cached.setPortfolioType(PortfolioType.HOLDINGS);
        when(portfolioCache.getPortfolioDetail(USER, pid)).thenReturn(Optional.of(cached));
        WhatIfSeriesResult r = org.mockito.Mockito.mock(WhatIfSeriesResult.class);
        when(whatIfService.computeSeries(cached, "STOCK", "THYAO.IS", List.of("gold"))).thenReturn(r);
        assertThat(service.getPortfolioWhatIfSeries(USER, pid, "STOCK", "THYAO.IS", List.of("gold")))
                .isSameAs(r);
    }

    @Test
    @DisplayName("getPortfolioWhatIfSeries: watchlist rejected")
    void getPortfolioWhatIfSeries_watchlistRejected() {
        UUID pid = UUID.randomUUID();
        PortfolioResponse cached = new PortfolioResponse();
        cached.setPortfolioType(PortfolioType.WATCHLIST);
        when(portfolioCache.getPortfolioDetail(USER, pid)).thenReturn(Optional.of(cached));
        assertThatThrownBy(() -> service.getPortfolioWhatIfSeries(USER, pid, "STOCK", "X", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getPortfolioWhatIfSimSeries: verifies ownership then delegates")
    void getPortfolioWhatIfSimSeries_ok() {
        UUID pid = UUID.randomUUID();
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(pid, USER)).thenReturn(Optional.of(p));
        WhatIfSeriesResult r = org.mockito.Mockito.mock(WhatIfSeriesResult.class);
        LocalDate d = LocalDate.of(2024, 1, 1);
        BigDecimal amt = new BigDecimal("1000");
        when(whatIfService.computeSimSeries("STOCK", "X", amt, d, List.of())).thenReturn(r);
        assertThat(service.getPortfolioWhatIfSimSeries(USER, pid, "STOCK", "X", amt, d, List.of()))
                .isSameAs(r);
    }

    @Test
    @DisplayName("getPortfolioWhatIfSimSeries: ownership failure rejected")
    void getPortfolioWhatIfSimSeries_notFound() {
        UUID pid = UUID.randomUUID();
        when(portfolioPersistence.findByIdAndUserId(pid, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPortfolioWhatIfSimSeries(
                USER, pid, "STOCK", "X", BigDecimal.ONE, LocalDate.now(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updatePortfolio ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updatePortfolio: blank user rejected")
    void updatePortfolio_blankUser() {
        assertThatThrownBy(() -> service.updatePortfolio("", UUID.randomUUID(), new UpdatePortfolioRequest()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updatePortfolio: not found rejected")
    void updatePortfolio_notFound() {
        UUID pid = UUID.randomUUID();
        when(portfolioPersistence.findByIdAndUserId(pid, USER)).thenReturn(Optional.empty());
        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName("X");
        assertThatThrownBy(() -> service.updatePortfolio(USER, pid, req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updatePortfolio: rename to existing name rejected")
    void updatePortfolio_dupName() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.existsByUserIdAndName(USER, "Taken")).thenReturn(true);
        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName("Taken");
        assertThatThrownBy(() -> service.updatePortfolio(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("updatePortfolio: name + description updated, cache evicted")
    void updatePortfolio_ok() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName("Renamed");
        req.setDescription("new desc");

        service.updatePortfolio(USER, p.getId(), req);

        assertThat(p.getName()).isEqualTo("Renamed");
        assertThat(p.getDescription()).isEqualTo("new desc");
        verify(portfolioCache).evictListAndDetail(USER, p.getId());
    }

    @Test
    @DisplayName("updatePortfolio: same name does not check duplicate")
    void updatePortfolio_sameName() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName(p.getName()); // unchanged
        req.setDescription(p.getDescription());

        service.updatePortfolio(USER, p.getId(), req);

        verify(portfolioPersistence, never()).existsByUserIdAndName(any(), any());
    }

    // ── deletePortfolio ──────────────────────────────────────────────────────

    @Test
    @DisplayName("deletePortfolio: not found rejected")
    void deletePortfolio_notFound() {
        UUID pid = UUID.randomUUID();
        when(portfolioPersistence.findByIdAndUserId(pid, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deletePortfolio(USER, pid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deletePortfolio: deletes + evicts watchlist cache")
    void deletePortfolio_ok() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));

        service.deletePortfolio(USER, p.getId());

        verify(portfolioPersistence).deletePortfolio(p);
        verify(portfolioCache).evictListDetailAndWatchlist(USER, p.getId());
    }

    // ── deleteTransaction ────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteTransaction: portfolio not found rejected")
    void deleteTransaction_notFound() {
        UUID pid = UUID.randomUUID();
        when(portfolioPersistence.findByIdAndUserId(pid, USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteTransaction(USER, pid, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deleteTransaction: transaction not found rejected")
    void deleteTransaction_txNotFound() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        UUID txId = UUID.randomUUID();
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.deleteTransactionByIdForPortfolioAndUser(txId, p.getId(), USER))
                .thenReturn(0);
        assertThatThrownBy(() -> service.deleteTransaction(USER, p.getId(), txId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    @DisplayName("deleteTransaction: success reloads + evicts")
    void deleteTransaction_ok() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        UUID txId = UUID.randomUUID();
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.deleteTransactionByIdForPortfolioAndUser(txId, p.getId(), USER))
                .thenReturn(1);

        service.deleteTransaction(USER, p.getId(), txId);

        verify(portfolioCache).evictListAndDetail(USER, p.getId());
        // findByIdAndUserId called twice (guard + reload)
        verify(portfolioPersistence, times(2)).findByIdAndUserId(p.getId(), USER);
    }

    // ── getUserPortfolios ────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserPortfolios: cache hit returns cached list")
    void getUserPortfolios_cacheHit() {
        List<PortfolioResponse> cached = List.of(new PortfolioResponse());
        when(portfolioCache.getPortfolioList(USER)).thenReturn(Optional.of(cached));
        assertThat(service.getUserPortfolios(USER)).isSameAs(cached);
        verify(portfolioPersistence, never()).findByUserId(any());
    }

    @Test
    @DisplayName("getUserPortfolios: cache miss builds deferred + pre-warms bonds + caches")
    void getUserPortfolios_cacheMiss() {
        when(portfolioCache.getPortfolioList(USER)).thenReturn(Optional.empty());
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        p.getTransactions().add(tx(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "100", "73", null));
        when(portfolioPersistence.findByUserId(USER)).thenReturn(List.of(p));
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("TRT240227T17");
        h.setAssetType(AssetType.BOND);
        when(holdingsBuilder.buildWithClosed(any(), eq(true)))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(
                        new java.util.ArrayList<>(List.of(h)), List.of()));
        EvdsBondInstrument bond = new EvdsBondInstrument();
        bond.setCategory(BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(bond);

        List<PortfolioResponse> resp = service.getUserPortfolios(USER);

        assertThat(resp).hasSize(1);
        // called for pre-warm + per-tx parScale lookup
        verify(evdsBondService, org.mockito.Mockito.atLeastOnce()).getEvdsBondDetail("TRT240227T17");
        verify(holdingsBuilder).enrichHolding(h);
        verify(portfolioCache).putPortfolioList(eq(USER), any());
    }

    @Test
    @DisplayName("getUserPortfolios: empty portfolios still caches empty list")
    void getUserPortfolios_empty() {
        when(portfolioCache.getPortfolioList(USER)).thenReturn(Optional.empty());
        when(portfolioPersistence.findByUserId(USER)).thenReturn(List.of());

        List<PortfolioResponse> resp = service.getUserPortfolios(USER);

        assertThat(resp).isEmpty();
        verify(portfolioCache).putPortfolioList(USER, resp);
    }

    // ── watchlist ops ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getWatchlistItems: cache hit returns cached")
    void getWatchlistItems_cacheHit() {
        UUID pid = UUID.randomUUID();
        List<WatchlistItemResponse> cached = List.of(new WatchlistItemResponse());
        when(portfolioCache.getWatchlist(USER, pid)).thenReturn(Optional.of(cached));
        assertThat(service.getWatchlistItems(USER, pid)).isSameAs(cached);
    }

    @Test
    @DisplayName("getWatchlistItems: non-watchlist portfolio rejected")
    void getWatchlistItems_notWatchlist() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioCache.getWatchlist(USER, p.getId())).thenReturn(Optional.empty());
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.getWatchlistItems(USER, p.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only watchlist");
    }

    @Test
    @DisplayName("getWatchlistItems: cache miss enriches + maps + caches")
    void getWatchlistItems_cacheMiss() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        when(portfolioCache.getWatchlist(USER, p.getId())).thenReturn(Optional.empty());
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        WatchlistItem item = new WatchlistItem();
        item.setId(UUID.randomUUID());
        item.setSymbol("THYAO.IS");
        item.setAssetType(AssetType.STOCK);
        when(portfolioPersistence.findWatchlistByPortfolioId(p.getId())).thenReturn(List.of(item));

        List<WatchlistItemResponse> resp = service.getWatchlistItems(USER, p.getId());

        assertThat(resp).hasSize(1);
        verify(watchlistMarketEnrichment).enrich(any(WatchlistItemResponse.class));
        verify(portfolioCache).putWatchlist(eq(USER), eq(p.getId()), any());
    }

    @Test
    @DisplayName("addWatchlistItem: null request rejected")
    void addWatchlistItem_nullRequest() {
        assertThatThrownBy(() -> service.addWatchlistItem(USER, UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("addWatchlistItem: non-watchlist portfolio rejected")
    void addWatchlistItem_notWatchlist() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        AddWatchlistItemRequest req = new AddWatchlistItemRequest();
        req.setSymbol("X");
        req.setAssetType(AssetType.STOCK);
        assertThatThrownBy(() -> service.addWatchlistItem(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only watchlist");
    }

    @Test
    @DisplayName("addWatchlistItem: duplicate symbol rejected")
    void addWatchlistItem_duplicate() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.existsWatchlistItem(p.getId(), "GARAN", AssetType.STOCK))
                .thenReturn(true);
        AddWatchlistItemRequest req = new AddWatchlistItemRequest();
        req.setSymbol("garan");
        req.setAssetType(AssetType.STOCK);
        assertThatThrownBy(() -> service.addWatchlistItem(USER, p.getId(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in this watchlist");
    }

    @Test
    @DisplayName("addWatchlistItem: success normalizes symbol, snapshots start, evicts cache")
    void addWatchlistItem_ok() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.existsWatchlistItem(p.getId(), "btc", AssetType.CRYPTO))
                .thenReturn(false);
        when(portfolioPersistence.saveWatchlistItem(any())).thenAnswer(inv -> {
            WatchlistItem it = inv.getArgument(0);
            it.setId(UUID.randomUUID());
            return it;
        });
        // start snapshot enrich populates lastPrice/currency on the temp response
        org.mockito.Mockito.doAnswer(inv -> {
            WatchlistItemResponse r = inv.getArgument(0);
            r.setLastPrice(new BigDecimal("50000"));
            r.setCurrency("USD");
            return null;
        }).when(watchlistMarketEnrichment).enrich(any(WatchlistItemResponse.class));

        AddWatchlistItemRequest req = new AddWatchlistItemRequest();
        req.setSymbol("BTC");
        req.setAssetType(AssetType.CRYPTO);
        req.setNotes("hodl");

        WatchlistItemResponse resp = service.addWatchlistItem(USER, p.getId(), req);

        assertThat(resp).isNotNull();
        ArgumentCaptor<WatchlistItem> cap = ArgumentCaptor.forClass(WatchlistItem.class);
        verify(portfolioPersistence).saveWatchlistItem(cap.capture());
        assertThat(cap.getValue().getSymbol()).isEqualTo("btc");
        assertThat(cap.getValue().getStartPrice()).isEqualByComparingTo("50000");
        verify(portfolioCache).evictListDetailAndWatchlist(USER, p.getId());
    }

    @Test
    @DisplayName("deleteWatchlistItem: non-watchlist portfolio rejected")
    void deleteWatchlistItem_notWatchlist() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        assertThatThrownBy(() -> service.deleteWatchlistItem(USER, p.getId(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only watchlist");
    }

    @Test
    @DisplayName("deleteWatchlistItem: item not found rejected")
    void deleteWatchlistItem_itemNotFound() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        UUID itemId = UUID.randomUUID();
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.findWatchlistItemByIdAndPortfolioId(itemId, p.getId()))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteWatchlistItem(USER, p.getId(), itemId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Watchlist item not found");
    }

    @Test
    @DisplayName("deleteWatchlistItem: success deletes + evicts")
    void deleteWatchlistItem_ok() {
        Portfolio p = portfolio(PortfolioType.WATCHLIST);
        UUID itemId = UUID.randomUUID();
        WatchlistItem item = new WatchlistItem();
        item.setId(itemId);
        item.setSymbol("BTC");
        item.setAssetType(AssetType.CRYPTO);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.findWatchlistItemByIdAndPortfolioId(itemId, p.getId()))
                .thenReturn(Optional.of(item));

        service.deleteWatchlistItem(USER, p.getId(), itemId);

        verify(portfolioPersistence).deleteWatchlistItemByIdAndPortfolioId(itemId, p.getId());
        verify(portfolioCache).evictListDetailAndWatchlist(USER, p.getId());
    }

    // ── toTransactionResponse / bondParScale / viop enrich (via toPortfolioResponse) ──

    @Test
    @DisplayName("toTransactionResponse: BOND gold-indexed → parScale 1; others → 100; FUTURE → multiplier+margin")
    void transactionResponse_parScaleAndViop() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        PortfolioTransaction goldBond = tx(AssetType.BOND, TransactionType.BUY, "TRGOLD", "10", "100", null);
        PortfolioTransaction normalBond = tx(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "10", "73", null);
        PortfolioTransaction fut = tx(AssetType.FUTURE, TransactionType.BUY, "F_AKBNK0626", "1", "10", "LONG");
        p.getTransactions().add(goldBond);
        p.getTransactions().add(normalBond);
        p.getTransactions().add(fut);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        EvdsBondInstrument gold = new EvdsBondInstrument();
        gold.setCategory(BondCategory.GOLD_INDEXED_BOND);
        EvdsBondInstrument normal = new EvdsBondInstrument();
        normal.setCategory(BondCategory.FIXED_COUPON_BOND);
        lenient().when(evdsBondService.getEvdsBondDetail("TRGOLD")).thenReturn(gold);
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17")).thenReturn(normal);

        ViopContractSpec spec = ViopContractSpec.fallback("AKBNK");
        when(viopSpecRegistry.resolveOrFallback("F_AKBNK0626")).thenReturn(spec);

        // trigger toPortfolioResponse via a no-op update (reuse updatePortfolio path)
        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName(p.getName());
        req.setDescription(p.getDescription());
        PortfolioResponse resp = service.updatePortfolio(USER, p.getId(), req);

        var byId = resp.getTransactions();
        assertThat(byId).hasSize(3);
        var goldResp = byId.stream().filter(t -> t.getSymbol().equals("TRGOLD")).findFirst().orElseThrow();
        var normalResp = byId.stream().filter(t -> t.getSymbol().equals("TRT240227T17")).findFirst().orElseThrow();
        var futResp = byId.stream().filter(t -> t.getAssetType() == AssetType.FUTURE).findFirst().orElseThrow();
        assertThat(goldResp.getBondParScale()).isEqualByComparingTo("1");
        assertThat(normalResp.getBondParScale()).isEqualByComparingTo("100");
        assertThat(futResp.getViopMultiplier()).isEqualByComparingTo(spec.multiplier());
        assertThat(futResp.getViopMarginRate()).isEqualByComparingTo(spec.marginRate());
        assertThat(futResp.getDirection()).isEqualTo("LONG");
    }

    @Test
    @DisplayName("toTransactionResponse: BOND detail lookup failure defaults parScale 100")
    void transactionResponse_bondLookupFailureDefaults100() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        p.getTransactions().add(tx(AssetType.BOND, TransactionType.BUY, "TRT240227T17", "10", "73", null));
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);
        lenient().when(evdsBondService.getEvdsBondDetail("TRT240227T17"))
                .thenThrow(new RuntimeException("evds down"));

        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName(p.getName());
        req.setDescription(p.getDescription());
        PortfolioResponse resp = service.updatePortfolio(USER, p.getId(), req);

        assertThat(resp.getTransactions().get(0).getBondParScale()).isEqualByComparingTo("100");
    }

    // ── toPortfolioResponse totals (currency-converted) ──────────────────────

    @Test
    @DisplayName("toPortfolioResponse: totals sum currency-converted holding values")
    void portfolioResponse_totals() {
        Portfolio p = portfolio(PortfolioType.HOLDINGS);
        when(portfolioPersistence.findByIdAndUserId(p.getId(), USER)).thenReturn(Optional.of(p));
        when(portfolioPersistence.savePortfolio(any())).thenReturn(p);

        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("THYAO.IS");
        h.setAssetType(AssetType.STOCK);
        h.setCurrency("TRY");
        h.setTotalCost(new BigDecimal("100"));
        h.setMarketValue(new BigDecimal("150"));
        h.setProfitLoss(new BigDecimal("50"));
        h.setRealizedGainLoss(new BigDecimal("10"));
        when(holdingsBuilder.buildWithClosed(any()))
                .thenReturn(new PortfolioHoldingsBuilder.BuildResult(
                        new java.util.ArrayList<>(List.of(h)), List.of()));

        UpdatePortfolioRequest req = new UpdatePortfolioRequest();
        req.setName(p.getName());
        req.setDescription(p.getDescription());
        PortfolioResponse resp = service.updatePortfolio(USER, p.getId(), req);

        assertThat(resp.getTotalCost()).isEqualByComparingTo("100");
        assertThat(resp.getTotalMarketValue()).isEqualByComparingTo("150");
        assertThat(resp.getTotalProfitLoss()).isEqualByComparingTo("50");
        assertThat(resp.getTotalRealizedProfitLoss()).isEqualByComparingTo("10");
        verify(realReturnEnricher).apply(resp);
    }
}
