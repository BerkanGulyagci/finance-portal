package com.finance.portal.newsletter.infrastructure.digest;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.economy.EconomyService;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import com.finance.portal.newsletter.application.model.DigestData;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioTransactionResponse;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.PortfolioCurrencyConverter;
import com.finance.portal.portfolio.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterDigestAdapterTest {

    @Mock PortfolioService portfolioService;
    @Mock EconomyService economyService;
    @Mock PortfolioCurrencyConverter currencyConverter;

    private NewsletterDigestAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NewsletterDigestAdapter(portfolioService, economyService, currencyConverter);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static PortfolioResponse holdingsPortfolio(String name) {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(UUID.randomUUID());
        p.setName(name);
        p.setPortfolioType(PortfolioType.HOLDINGS);
        return p;
    }

    private static PortfolioResponse watchlistPortfolio(String name) {
        PortfolioResponse p = new PortfolioResponse();
        p.setId(UUID.randomUUID());
        p.setName(name);
        p.setPortfolioType(PortfolioType.WATCHLIST);
        return p;
    }

    private static PortfolioHoldingResponse holding(String symbol, AssetType type, String currency,
                                                    BigDecimal marketValue, BigDecimal changePercent) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setAssetType(type);
        h.setCurrency(currency);
        h.setMarketValue(marketValue);
        h.setChangePercent(changePercent);
        return h;
    }

    private static PortfolioTransactionResponse tx(String symbol, TransactionType type,
                                                   BigDecimal qty, BigDecimal price, LocalDateTime when) {
        PortfolioTransactionResponse t = new PortfolioTransactionResponse();
        t.setSymbol(symbol);
        t.setTransactionType(type);
        t.setQuantity(qty);
        t.setPrice(price);
        t.setTransactionDate(when);
        return t;
    }

    private static EconomyIndicator indicator(String key, BigDecimal value, BigDecimal yoy) {
        EconomyIndicator i = new EconomyIndicator();
        i.setKey(key);
        i.setValue(value);
        i.setYoyChangePercent(yoy);
        return i;
    }

    // ── tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Hiç portföy yoksa boş ama tutarlı bir DigestData döner")
    void emptyPortfolios_returnsEmptyDigest() {
        when(portfolioService.getUserPortfolios("user-1")).thenReturn(List.of());
        when(economyService.getSummary()).thenReturn(List.of());

        DigestData data = adapter.buildFor("user-1");

        assertThat(data).isNotNull();
        assertThat(data.portfolioCount()).isZero();
        assertThat(data.totalValue()).isEqualByComparingTo("0");
        assertThat(data.totalProfitLoss()).isEqualByComparingTo("0");
        assertThat(data.totalProfitLossPercent()).isEqualByComparingTo("0");
        assertThat(data.dailyChangePercent()).isEqualByComparingTo("0");
        assertThat(data.allocation()).isEmpty();
        assertThat(data.portfolios()).isEmpty();
        assertThat(data.gainers()).isEmpty();
        assertThat(data.losers()).isEmpty();
        assertThat(data.recentTransactions()).isEmpty();
        assertThat(data.favorites()).isEmpty();
        assertThat(data.market()).isNotNull();
        assertThat(data.market().usdTry()).isNull();
    }

    @Test
    @DisplayName("Mutlu yol: toplamlar, dağılım, kazandıran/kaybettiren ve işlemler hesaplanır")
    void happyPath_aggregatesTotalsAllocationMoversAndTransactions() {
        PortfolioResponse p = holdingsPortfolio("Ana Portföy");
        p.setTotalMarketValue(new BigDecimal("1500"));
        p.setTotalCost(new BigDecimal("1000"));
        p.setTotalProfitLoss(new BigDecimal("500"));

        PortfolioHoldingResponse stock = holding("THYAO", AssetType.STOCK, "TRY",
                new BigDecimal("900"), new BigDecimal("5.0"));   // gainer
        PortfolioHoldingResponse crypto = holding("BTC", AssetType.CRYPTO, "USD",
                new BigDecimal("100"), new BigDecimal("-3.0"));  // loser
        p.setHoldings(List.of(stock, crypto));

        p.setTransactions(List.of(
                tx("THYAO", TransactionType.BUY, new BigDecimal("10"), new BigDecimal("50"),
                        LocalDateTime.now().minusDays(2)),
                tx("BTC", TransactionType.SELL, new BigDecimal("1"), new BigDecimal("100"),
                        LocalDateTime.now().minusDays(1))));

        when(portfolioService.getUserPortfolios("user-1")).thenReturn(List.of(p));
        when(economyService.getSummary()).thenReturn(List.of(
                indicator("usdTry", new BigDecimal("32.5"), null),
                indicator("bist100", new BigDecimal("10000"), null),
                indicator("gramAltin", new BigDecimal("2400"), null),
                indicator("tufe", new BigDecimal("64"), new BigDecimal("64.0")),
                indicator("politikaFaizi", new BigDecimal("50"), null)));

        // STOCK already TRY → 900; CRYPTO USD → convert to 600 TRY
        when(currencyConverter.toTry(new BigDecimal("900"), "TRY")).thenReturn(new BigDecimal("900"));
        when(currencyConverter.toTry(new BigDecimal("100"), "USD")).thenReturn(new BigDecimal("600"));

        DigestData data = adapter.buildFor("user-1");

        assertThat(data.portfolioCount()).isEqualTo(1);
        assertThat(data.totalValue()).isEqualByComparingTo("1500");
        assertThat(data.totalProfitLoss()).isEqualByComparingTo("500");
        // pct = 500 * 100 / 1000 = 50.00
        assertThat(data.totalProfitLossPercent()).isEqualByComparingTo("50.00");

        // allocation: 900 + 600 = 1500 total → Hisse 60.0, Kripto 40.0 (sorted desc)
        assertThat(data.allocation()).hasSize(2);
        assertThat(data.allocation().get(0).label()).isEqualTo("Hisse");
        assertThat(data.allocation().get(0).percent()).isEqualByComparingTo("60.0");
        assertThat(data.allocation().get(1).label()).isEqualTo("Kripto");
        assertThat(data.allocation().get(1).percent()).isEqualByComparingTo("40.0");

        // movers — en çok kazandıran ilk sırada THYAO, en çok kaybettiren ilk sırada BTC
        // (az holding'de top-N listeleri örtüşebilir; önemli olan sıralamanın ilk elemanı).
        assertThat(data.gainers().get(0).symbol()).isEqualTo("THYAO");
        assertThat(data.losers().get(0).symbol()).isEqualTo("BTC");

        // transactions: both present, sorted recent first
        assertThat(data.recentTransactions()).hasSize(2);
        assertThat(data.recentTransactions().get(0).symbol()).isEqualTo("BTC");
        assertThat(data.recentTransactions().get(0).type()).isEqualTo("SELL");
        // BTC total = qty(1) * price(100) = 100
        assertThat(data.recentTransactions().get(0).total()).isEqualByComparingTo("100");
        assertThat(data.recentTransactions().get(0).portfolioName()).isEqualTo("Ana Portföy");

        // market mapped from summary
        assertThat(data.market().usdTry()).isEqualByComparingTo("32.5");
        assertThat(data.market().bist100()).isEqualByComparingTo("10000");
        assertThat(data.market().inflationYoy()).isEqualByComparingTo("64.0");
        assertThat(data.market().policyRate()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("İzleme listesi favorileri toplanır")
    void watchlist_favoritesCollected() {
        PortfolioResponse w = watchlistPortfolio("Takip");

        when(portfolioService.getUserPortfolios("user-1")).thenReturn(List.of(w));
        when(economyService.getSummary()).thenReturn(List.of());

        WatchlistItemResponse it = new WatchlistItemResponse();
        it.setSymbol("AAPL");
        it.setAssetType(AssetType.STOCK);
        it.setLastPrice(new BigDecimal("190"));
        it.setChangePercent(new BigDecimal("1.2"));
        when(portfolioService.getWatchlistItems("user-1", w.getId())).thenReturn(List.of(it));

        DigestData data = adapter.buildFor("user-1");

        // watchlist is not counted as a portfolio
        assertThat(data.portfolioCount()).isZero();
        assertThat(data.favorites()).hasSize(1);
        DigestData.Fav fav = data.favorites().get(0);
        assertThat(fav.symbol()).isEqualTo("AAPL");
        assertThat(fav.typeLabel()).isEqualTo("Hisse");
        assertThat(fav.lastPrice()).isEqualByComparingTo("190");
        assertThat(fav.changePercent()).isEqualByComparingTo("1.2");
    }

    @Test
    @DisplayName("getWatchlistItems patlarsa favoriler boş kalır, akış bozulmaz")
    void watchlistThrows_gracefulEmptyFavorites() {
        PortfolioResponse w = watchlistPortfolio("Takip");

        when(portfolioService.getUserPortfolios("user-1")).thenReturn(List.of(w));
        when(economyService.getSummary()).thenReturn(List.of());
        when(portfolioService.getWatchlistItems(anyString(), any(UUID.class)))
                .thenThrow(new RuntimeException("boom"));

        DigestData data = adapter.buildFor("user-1");

        assertThat(data).isNotNull();
        assertThat(data.favorites()).isEmpty();
    }

    @Test
    @DisplayName("economyService.getSummary patlarsa market alanları null ile döner")
    void economyThrows_marketAllNull() {
        when(portfolioService.getUserPortfolios("user-1")).thenReturn(List.of());
        when(economyService.getSummary()).thenThrow(new RuntimeException("evds down"));

        DigestData data = adapter.buildFor("user-1");

        assertThat(data.market()).isNotNull();
        assertThat(data.market().usdTry()).isNull();
        assertThat(data.market().bist100()).isNull();
        assertThat(data.market().gramAltin()).isNull();
        assertThat(data.market().inflationYoy()).isNull();
        assertThat(data.market().policyRate()).isNull();
    }

    @Test
    @DisplayName("null holdings/transactions ve null transactionType güvenle ele alınır")
    void nullCollectionsAndNullTxType_handledGracefully() {
        PortfolioResponse p = holdingsPortfolio("Bos");
        p.setHoldings(null);
        // one transaction with null transactionType → defaults to "BUY", null qty/price → 0 total
        PortfolioTransactionResponse t = new PortfolioTransactionResponse();
        t.setSymbol("XYZ");
        t.setTransactionType(null);
        t.setTransactionDate(LocalDateTime.now());
        p.setTransactions(List.of(t));

        when(portfolioService.getUserPortfolios("user-1")).thenReturn(List.of(p));
        when(economyService.getSummary()).thenReturn(List.of());

        DigestData data = adapter.buildFor("user-1");

        assertThat(data.portfolioCount()).isEqualTo(1);
        assertThat(data.allocation()).isEmpty();
        assertThat(data.recentTransactions()).hasSize(1);
        assertThat(data.recentTransactions().get(0).type()).isEqualTo("BUY");
        assertThat(data.recentTransactions().get(0).total()).isEqualByComparingTo("0");
        // null totals on portfolio → treated as zero
        assertThat(data.totalValue()).isEqualByComparingTo("0");
        assertThat(data.totalProfitLossPercent()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Sıfır/negatif TL değerli holding dağılıma katılmaz; mover name fallback symbol'e düşer")
    void nonPositiveTryHolding_skippedAndMoverNameFallback() {
        PortfolioResponse p = holdingsPortfolio("P");
        p.setTotalMarketValue(new BigDecimal("100"));
        p.setTotalCost(new BigDecimal("100"));
        p.setTotalProfitLoss(BigDecimal.ZERO);

        PortfolioHoldingResponse zeroVal = holding("ZRO", AssetType.FX, "USD",
                new BigDecimal("0"), new BigDecimal("2.0"));
        // name blank → toMover falls back to symbol
        zeroVal.setName("  ");
        p.setHoldings(List.of(zeroVal));

        when(portfolioService.getUserPortfolios("user-1")).thenReturn(List.of(p));
        when(economyService.getSummary()).thenReturn(List.of());
        // converter returns zero → signum not > 0 → excluded from allocation
        lenient().when(currencyConverter.toTry(any(), anyString())).thenReturn(BigDecimal.ZERO);

        DigestData data = adapter.buildFor("user-1");

        assertThat(data.allocation()).isEmpty();
        assertThat(data.dailyChangePercent()).isEqualByComparingTo("0");
        // holding has changePercent → still a mover, name falls back to symbol
        assertThat(data.gainers()).hasSize(1);
        assertThat(data.gainers().get(0).symbol()).isEqualTo("ZRO");
        assertThat(data.gainers().get(0).name()).isEqualTo("ZRO");
    }
}
