package com.finance.portal.market.application.movers;

import com.finance.portal.market.application.commodity.CommodityDto;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.currency.BankCurrencyRateDto;
import com.finance.portal.market.application.currency.BankCurrencyService;
import com.finance.portal.market.application.movers.model.MarketMover;
import com.finance.portal.market.application.movers.model.MoversCategory;
import com.finance.portal.market.application.stock.StockPageResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketMoversService} — focuses on the cross-asset top-N
 * gainers/losers selection: descending sort for gainers, ascending for losers,
 * top-N limiting, null/sign filtering, and empty/failure handling.
 *
 * <p>Pure JUnit 5 + Mockito (no Spring context). The four data-source services are
 * mocked to return controlled lists with known {@code changePercent} values.
 */
@ExtendWith(MockitoExtension.class)
class MarketMoversServiceTest {

    @Mock
    private CryptoMarketService cryptoMarketService;
    @Mock
    private StockQueryService stockQueryService;
    @Mock
    private BankCurrencyService bankCurrencyService;
    @Mock
    private YahooCommodityService commodityService;

    @InjectMocks
    private MarketMoversService service;

    /** Default: every source returns empty so a given test only wires the one(s) it cares about. */
    @BeforeEach
    void emptyDefaults() {
        lenient().when(cryptoMarketService.getAllCoins("try")).thenReturn(new ArrayList<>());
        lenient().when(stockQueryService.getPagedStockSummaries(anyInt(), anyInt()))
                .thenReturn(stockPage(new ArrayList<>(), 0));
        lenient().when(bankCurrencyService.getAllBankRates()).thenReturn(new ArrayList<>());
        lenient().when(commodityService.listEnabledCommodities()).thenReturn(new ArrayList<>());
    }

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    void getMovers_returnsFourCategoriesInOrder() {
        List<MoversCategory> result = service.getMovers(5);

        assertThat(result).extracting(MoversCategory::getKey)
                .containsExactly("crypto", "stock", "fx", "commodity");
        assertThat(result).extracting(MoversCategory::getLabel)
                .containsExactly("Kripto", "BIST Hisse", "Döviz", "Emtia");
    }

    @Test
    void getMovers_allEmpty_categoriesHaveEmptyGainersAndLosers() {
        List<MoversCategory> result = service.getMovers(5);

        for (MoversCategory c : result) {
            assertThat(c.getGainers()).isEmpty();
            assertThat(c.getLosers()).isEmpty();
        }
    }

    // ── Crypto: sorting + sign filtering ───────────────────────────────────────

    @Test
    void crypto_gainersSortedDescending_losersSortedAscending() {
        when(cryptoMarketService.getAllCoins("try")).thenReturn(List.of(
                coin("btc", "BTC", "Bitcoin", "3.0"),
                coin("eth", "ETH", "Ethereum", "7.5"),
                coin("ada", "ADA", "Cardano", "-2.0"),
                coin("sol", "SOL", "Solana", "-9.0"),
                coin("xrp", "XRP", "Ripple", "1.0")
        ));

        MoversCategory crypto = cryptoCategory();

        // gainers: only positive, sorted descending by changePercent
        assertThat(crypto.getGainers()).extracting(MarketMover::getSymbol)
                .containsExactly("ETH", "BTC", "XRP");
        assertThat(crypto.getGainers()).extracting(MarketMover::getChangePercent)
                .containsExactly(bd("7.5"), bd("3.0"), bd("1.0"));

        // losers: only negative, sorted ascending (most negative first)
        assertThat(crypto.getLosers()).extracting(MarketMover::getSymbol)
                .containsExactly("SOL", "ADA");
        assertThat(crypto.getLosers()).extracting(MarketMover::getChangePercent)
                .containsExactly(bd("-9.0"), bd("-2.0"));
    }

    @Test
    void crypto_appliesTopNLimit() {
        when(cryptoMarketService.getAllCoins("try")).thenReturn(List.of(
                coin("a", "A", "A", "10.0"),
                coin("b", "B", "B", "8.0"),
                coin("c", "C", "C", "6.0"),
                coin("d", "D", "D", "4.0"),
                coin("e", "E", "E", "2.0")
        ));

        MoversCategory crypto = cryptoCategoryWithLimit(2);

        assertThat(crypto.getGainers()).hasSize(2);
        assertThat(crypto.getGainers()).extracting(MarketMover::getSymbol)
                .containsExactly("A", "B");
    }

    @Test
    void crypto_zeroChangeIsExcludedFromBothLists() {
        when(cryptoMarketService.getAllCoins("try")).thenReturn(List.of(
                coin("flat", "FLAT", "Flat", "0.0"),
                coin("up", "UP", "Up", "1.0"),
                coin("dn", "DN", "Down", "-1.0")
        ));

        MoversCategory crypto = cryptoCategory();

        assertThat(crypto.getGainers()).extracting(MarketMover::getSymbol).containsExactly("UP");
        assertThat(crypto.getLosers()).extracting(MarketMover::getSymbol).containsExactly("DN");
    }

    @Test
    void crypto_skipsItemsWithNullChangeOrNullPrice() {
        List<CryptoMarketItem> coins = new ArrayList<>();
        coins.add(coin("ok", "OK", "Ok", "5.0"));
        coins.add(coinRaw("nullpct", "NP", "NullPct", bd("100"), null));   // null changePercent
        coins.add(coinRaw("nullprice", "NPR", "NullPrice", null, bd("5"))); // null currentPrice
        when(cryptoMarketService.getAllCoins("try")).thenReturn(coins);

        MoversCategory crypto = cryptoCategory();

        assertThat(crypto.getGainers()).extracting(MarketMover::getSymbol).containsExactly("OK");
        assertThat(crypto.getLosers()).isEmpty();
    }

    @Test
    void crypto_dataSourceThrows_yieldsEmptyCategoryNotPropagated() {
        when(cryptoMarketService.getAllCoins("try")).thenThrow(new RuntimeException("boom"));

        MoversCategory crypto = cryptoCategory();

        assertThat(crypto.getGainers()).isEmpty();
        assertThat(crypto.getLosers()).isEmpty();
    }

    @Test
    void crypto_moverFieldsAreMappedCorrectly() {
        when(cryptoMarketService.getAllCoins("try")).thenReturn(List.of(
                coinRawFull("bitcoin", "btc", "Bitcoin", bd("123.45"), bd("4.2"), "img-url")
        ));

        MarketMover m = cryptoCategory().getGainers().get(0);

        assertThat(m.getType()).isEqualTo("CRYPTO");
        assertThat(m.getId()).isEqualTo("bitcoin");
        assertThat(m.getSymbol()).isEqualTo("BTC"); // upper-cased
        assertThat(m.getName()).isEqualTo("Bitcoin");
        assertThat(m.getPrice()).isEqualByComparingTo(bd("123.45"));
        assertThat(m.getCurrency()).isEqualTo("TRY");
        assertThat(m.getChangePercent()).isEqualByComparingTo(bd("4.2"));
        assertThat(m.getImage()).isEqualTo("img-url");
    }

    // ── Stock: pagination + filtering ──────────────────────────────────────────

    @Test
    void stock_aggregatesAcrossPages_andSortsAcrossThem() {
        StockSummary p0a = stock("AAA", "Alpha", "2.0");
        StockSummary p0b = stock("BBB", "Beta", "-5.0");
        StockSummary p1a = stock("CCC", "Gamma", "9.0");
        StockSummary p1b = stock("DDD", "Delta", "-1.0");

        when(stockQueryService.getPagedStockSummaries(eq(0), anyInt()))
                .thenReturn(stockPage(List.of(p0a, p0b), 2));
        when(stockQueryService.getPagedStockSummaries(eq(1), anyInt()))
                .thenReturn(stockPage(List.of(p1a, p1b), 2));

        MoversCategory stock = categoryByKey(service.getMovers(5), "stock");

        assertThat(stock.getGainers()).extracting(MarketMover::getSymbol)
                .containsExactly("CCC", "AAA"); // 9.0 then 2.0 across pages
        assertThat(stock.getLosers()).extracting(MarketMover::getSymbol)
                .containsExactly("BBB", "DDD"); // -5.0 then -1.0
    }

    @Test
    void stock_skipsRowsWithNullSymbolOrNullChange() {
        StockSummary good = stock("GOOD", "Good", "3.0");
        StockSummary noSym = stock(null, "NoSym", "10.0");
        StockSummary noChg = stock("NOCHG", "NoChg", null);

        when(stockQueryService.getPagedStockSummaries(eq(0), anyInt()))
                .thenReturn(stockPage(List.of(good, noSym, noChg), 1));

        MoversCategory stock = categoryByKey(service.getMovers(5), "stock");

        assertThat(stock.getGainers()).extracting(MarketMover::getSymbol).containsExactly("GOOD");
    }

    @Test
    void stock_dataSourceThrows_yieldsEmptyCategory() {
        when(stockQueryService.getPagedStockSummaries(eq(0), anyInt()))
                .thenThrow(new RuntimeException("down"));

        MoversCategory stock = categoryByKey(service.getMovers(5), "stock");

        assertThat(stock.getGainers()).isEmpty();
        assertThat(stock.getLosers()).isEmpty();
    }

    // ── FX: per-currency averaging ─────────────────────────────────────────────

    @Test
    void fx_averagesDailyChangePercentPerCurrencyCode() {
        // USD across two banks: avg(2.0, 4.0) = 3.0 ; EUR single bank: -1.5
        BankCurrencyRateDto usdA = fxRate("USD", "Dolar", 2.0, 30.0);
        BankCurrencyRateDto usdB = fxRate("usd", "Dolar", 4.0, 32.0);
        BankCurrencyRateDto eur = fxRate("EUR", "Euro", -1.5, 35.0);

        when(bankCurrencyService.getAllBankRates()).thenReturn(List.of(usdA, usdB, eur));

        MoversCategory fx = categoryByKey(service.getMovers(5), "fx");

        assertThat(fx.getGainers()).extracting(MarketMover::getSymbol).containsExactly("USD");
        assertThat(fx.getGainers().get(0).getChangePercent()).isEqualByComparingTo(bd("3.00"));
        assertThat(fx.getLosers()).extracting(MarketMover::getSymbol).containsExactly("EUR");
        assertThat(fx.getLosers().get(0).getChangePercent()).isEqualByComparingTo(bd("-1.50"));
    }

    @Test
    void fx_skipsCurrenciesWithoutAnyDailyChangePercent() {
        BankCurrencyRateDto noPct = fxRate("GBP", "Sterlin", null, 40.0);
        BankCurrencyRateDto withPct = fxRate("USD", "Dolar", 1.0, 30.0);

        when(bankCurrencyService.getAllBankRates()).thenReturn(List.of(noPct, withPct));

        MoversCategory fx = categoryByKey(service.getMovers(5), "fx");

        assertThat(fx.getGainers()).extracting(MarketMover::getSymbol).containsExactly("USD");
        assertThat(fx.getLosers()).isEmpty();
    }

    // ── Commodity: per-symbol spot lookup ──────────────────────────────────────

    @Test
    void commodity_buildsMoversFromSpotChangePercent() {
        CommodityDto gold = commodity("GC=F", "Altın");
        CommodityDto oil = commodity("CL=F", "Petrol");
        when(commodityService.listEnabledCommodities()).thenReturn(List.of(gold, oil));
        when(commodityService.getSpot("GC=F")).thenReturn(spot(bd("2000"), "1.2", "USD"));
        when(commodityService.getSpot("CL=F")).thenReturn(spot(bd("80"), "-3.4", "USD"));

        MoversCategory c = categoryByKey(service.getMovers(5), "commodity");

        assertThat(c.getGainers()).extracting(MarketMover::getSymbol).containsExactly("Altın");
        assertThat(c.getLosers()).extracting(MarketMover::getSymbol).containsExactly("Petrol");
        assertThat(c.getLosers().get(0).getChangePercent()).isEqualByComparingTo(bd("-3.4"));
    }

    @Test
    void commodity_skipsNullSpotOrNullChangePercent() {
        CommodityDto a = commodity("A=F", "A");
        CommodityDto b = commodity("B=F", "B");
        CommodityDto good = commodity("G=F", "Good");
        when(commodityService.listEnabledCommodities()).thenReturn(List.of(a, b, good));
        when(commodityService.getSpot("A=F")).thenReturn(null);
        when(commodityService.getSpot("B=F")).thenReturn(spot(bd("1"), null, "USD"));
        when(commodityService.getSpot("G=F")).thenReturn(spot(bd("10"), "5.0", "USD"));

        MoversCategory c = categoryByKey(service.getMovers(5), "commodity");

        assertThat(c.getGainers()).extracting(MarketMover::getSymbol).containsExactly("Good");
    }

    @Test
    void commodity_oneSpotThrows_otherStillIncluded() {
        CommodityDto bad = commodity("BAD=F", "Bad");
        CommodityDto good = commodity("OK=F", "Ok");
        when(commodityService.listEnabledCommodities()).thenReturn(List.of(bad, good));
        when(commodityService.getSpot("BAD=F")).thenThrow(new RuntimeException("spot fail"));
        when(commodityService.getSpot("OK=F")).thenReturn(spot(bd("5"), "2.0", "USD"));

        MoversCategory c = categoryByKey(service.getMovers(5), "commodity");

        assertThat(c.getGainers()).extracting(MarketMover::getSymbol).containsExactly("Ok");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private MoversCategory cryptoCategory() {
        return categoryByKey(service.getMovers(5), "crypto");
    }

    private MoversCategory cryptoCategoryWithLimit(int limit) {
        return categoryByKey(service.getMovers(limit), "crypto");
    }

    private static MoversCategory categoryByKey(List<MoversCategory> cats, String key) {
        return cats.stream().filter(c -> key.equals(c.getKey())).findFirst().orElseThrow();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static CryptoMarketItem coin(String id, String symbol, String name, String pct) {
        return coinRawFull(id, symbol, name, bd("100"), bd(pct), null);
    }

    private static CryptoMarketItem coinRaw(String id, String symbol, String name,
                                            BigDecimal price, BigDecimal pct) {
        return coinRawFull(id, symbol, name, price, pct, null);
    }

    private static CryptoMarketItem coinRawFull(String id, String symbol, String name,
                                                BigDecimal price, BigDecimal pct, String image) {
        return new CryptoMarketItem(
                id, symbol, name, image,
                price,            // currentPrice
                null, null, null, // marketCap, rank, totalVolume
                null, null,       // high24h, low24h
                null,             // priceChange24h
                pct,              // priceChangePercentage24h
                null, null,       // 1h, 7d
                null);            // lastUpdated
    }

    private static StockSummary stock(String symbol, String name, String pct) {
        StockSummary s = new StockSummary();
        s.setSymbol(symbol);
        s.setName(name);
        s.setPrice(bd("100"));
        s.setChangePercent(pct == null ? null : bd(pct));
        return s;
    }

    private static StockPageResponse stockPage(List<StockSummary> content, int totalElements) {
        StockPageResponse r = new StockPageResponse();
        r.setContent(content);
        r.setTotalElements(totalElements);
        r.setTotalPages(content.isEmpty() ? 0 : 2); // force a second page fetch when non-empty
        r.setSize(20);
        return r;
    }

    private static BankCurrencyRateDto fxRate(String code, String name, Double pct, Double last) {
        BankCurrencyRateDto r = new BankCurrencyRateDto();
        r.setCurrencyCode(code);
        r.setCurrencyName(name);
        r.setDailyChangePercent(pct);
        r.setLastRate(last);
        return r;
    }

    private static CommodityDto commodity(String symbol, String displayTr) {
        CommodityDto c = new CommodityDto();
        c.setSymbol(symbol);
        c.setDisplayNameTr(displayTr);
        c.setEnabled(true);
        return c;
    }

    private static CommoditySpotDto spot(BigDecimal price, String pct, String currency) {
        CommoditySpotDto s = new CommoditySpotDto();
        s.setDisplayPrice(price);
        s.setChangePercent(pct == null ? null : bd(pct));
        s.setDisplayCurrency(currency);
        return s;
    }
}
