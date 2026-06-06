package com.finance.portal.market.application.crypto;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.crypto.port.CoinGeckoPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CryptoMarketService} list / lookup / detail behaviour.
 * Resilience4j annotations (@Cacheable/@CircuitBreaker/@Retry) are inert when the
 * service is constructed directly, so methods run their plain bodies.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CryptoMarketServiceTest {

    @Mock
    private CoinGeckoPort coinGeckoPort;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private CentralIntegrationLogService integrationLogService;
    @Mock
    private LastKnownGoodCache lkg;
    @Mock
    private CryptoDescriptionTranslationService descriptionTranslationService;

    /** LKG sarmalayıcı bu birim testlerde şeffaf olmalı: doğrudan asıl çekimi (Supplier) çalıştır. */
    @BeforeEach
    void stubLkgPassThrough() {
        when(lkg.resilient(any(), any(), any(), any()))
                .thenAnswer(inv -> inv.<Supplier<?>>getArgument(3).get());
    }

    private CryptoMarketService newService() {
        return new CryptoMarketService(coinGeckoPort, cacheManager, integrationLogService, lkg,
                descriptionTranslationService);
    }

    private static CryptoMarketItem item(String id, String symbol, String name, BigDecimal price) {
        return new CryptoMarketItem(
                id, symbol, name, "https://img/" + id + ".png",
                price,                       // currentPrice
                new BigDecimal("1000000"),   // marketCap
                1,                           // marketCapRank
                new BigDecimal("50000"),     // totalVolume
                new BigDecimal("110"),       // high24h
                new BigDecimal("90"),        // low24h
                new BigDecimal("5"),         // priceChange24h
                new BigDecimal("2.5"),       // priceChangePercentage24h
                new BigDecimal("0.5"),       // priceChangePercentage1h
                new BigDecimal("7.7"),       // priceChangePercentage7d
                "2026-01-01T00:00:00Z"       // lastUpdated
        );
    }

    // ---------- getCryptos(page,size) defaults to try & maps page+1 ----------

    @Test
    void getCryptos_default_usesTryCurrencyAndCoinGeckoPagePlusOne() {
        CryptoMarketService service = newService();
        List<CryptoMarketItem> expected = List.of(item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000")));
        when(coinGeckoPort.fetchMarkets(1, 20, "try")).thenReturn(expected);

        List<CryptoMarketItem> result = service.getCryptos(0, 20);

        assertThat(result).isEqualTo(expected);
        // page 0 -> coingecko page 1
        verify(coinGeckoPort).fetchMarkets(1, 20, "try");
    }

    @Test
    void getCryptos_withCurrency_normalizesAndOffsetsPage() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchMarkets(3, 50, "usd")).thenReturn(List.of());

        service.getCryptos(2, 50, "  USD  ");

        verify(coinGeckoPort).fetchMarkets(3, 50, "usd");
    }

    @Test
    void getCryptos_blankCurrency_fallsBackToTry() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchMarkets(1, 10, "try")).thenReturn(List.of());

        service.getCryptos(0, 10, "   ");

        verify(coinGeckoPort).fetchMarkets(1, 10, "try");
    }

    @Test
    void getCryptos_nullCurrency_fallsBackToTry() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchMarkets(1, 10, "try")).thenReturn(List.of());

        service.getCryptos(0, 10, null);

        verify(coinGeckoPort).fetchMarkets(1, 10, "try");
    }

    @Test
    void getCryptos_negativePage_throws() {
        CryptoMarketService service = newService();
        assertThatThrownBy(() -> service.getCryptos(-1, 10, "try"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        verifyNoInteractions(coinGeckoPort);
    }

    @Test
    void getCryptos_sizeTooSmall_throws() {
        CryptoMarketService service = newService();
        assertThatThrownBy(() -> service.getCryptos(0, 0, "try"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void getCryptos_sizeTooLarge_throws() {
        CryptoMarketService service = newService();
        assertThatThrownBy(() -> service.getCryptos(0, 251, "try"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void getCryptos_sizeAtBoundary250_isAccepted() {
        CryptoMarketService service = newService();
        when(coinGeckoPort.fetchMarkets(1, 250, "try")).thenReturn(List.of());

        service.getCryptos(0, 250, "try");

        verify(coinGeckoPort).fetchMarkets(1, 250, "try");
    }

    // ---------- getAllCoins ----------

    @Test
    void getAllCoins_cacheHit_returnsCachedWithoutCallingPort() {
        CryptoMarketService service = newService();
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        List<CryptoMarketItem> cached = List.of(item("bitcoin", "btc", "Bitcoin", new BigDecimal("1")));
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:all")).thenReturn(() -> cached);

        List<CryptoMarketItem> result = service.getAllCoins("try");

        assertThat(result).isSameAs(cached);
        verify(coinGeckoPort, never()).fetchMarkets(anyInt(), anyInt(), anyString());
    }

    @Test
    void getAllCoins_emptyFirstPage_returnsEmptyAndDoesNotCache() {
        CryptoMarketService service = newService();
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:all")).thenReturn(null);
        when(coinGeckoPort.fetchMarkets(1, 250, "try")).thenReturn(List.of());

        List<CryptoMarketItem> result = service.getAllCoins("try");

        assertThat(result).isEmpty();
        verify(cache, never()).put(eq("try:all"), any());
        // loop breaks on empty first page; only page 1 fetched
        verify(coinGeckoPort, times(1)).fetchMarkets(anyInt(), eq(250), eq("try"));
    }

    @Test
    void getAllCoins_firstPageThenEmptySecond_putsResultInCache() {
        CryptoMarketService service = newService();
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:all")).thenReturn(null);
        List<CryptoMarketItem> page1 = List.of(item("bitcoin", "btc", "Bitcoin", new BigDecimal("1")));
        when(coinGeckoPort.fetchMarkets(1, 250, "try")).thenReturn(page1);
        when(coinGeckoPort.fetchMarkets(2, 250, "try")).thenReturn(List.of());

        List<CryptoMarketItem> result = service.getAllCoins("try");

        assertThat(result).containsExactlyElementsOf(page1);
        verify(cache).put("try:all", result);
    }

    @Test
    void getAllCoins_firstPageFails_throwsExternalApiException() {
        CryptoMarketService service = newService();
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(null);
        when(coinGeckoPort.fetchMarkets(1, 250, "try"))
                .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.getAllCoins(null))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getAllCoins_nullCache_stillFetchesAndReturns() {
        CryptoMarketService service = newService();
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(null);
        List<CryptoMarketItem> page1 = List.of(item("bitcoin", "btc", "Bitcoin", new BigDecimal("1")));
        when(coinGeckoPort.fetchMarkets(1, 250, "usd")).thenReturn(page1);
        when(coinGeckoPort.fetchMarkets(2, 250, "usd")).thenReturn(List.of());

        List<CryptoMarketItem> result = service.getAllCoins("USD");

        assertThat(result).containsExactlyElementsOf(page1);
    }

    // ---------- findBySymbol ----------

    @Test
    void findBySymbol_found_returnsMatchingItem() {
        CryptoMarketService service = newService();
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        CryptoMarketItem btc = item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000"));
        CryptoMarketItem eth = item("ethereum", "eth", "Ethereum", new BigDecimal("200000"));
        when(cache.get("try:all")).thenReturn(() -> List.of(btc, eth));

        CryptoMarketItem result = service.findBySymbol("  ETH  ");

        assertThat(result).isSameAs(eth);
    }

    @Test
    void findBySymbol_notFound_throwsResourceNotFound() {
        CryptoMarketService service = newService();
        Cache cache = org.mockito.Mockito.mock(Cache.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:all")).thenReturn(() -> List.of(item("bitcoin", "btc", "Bitcoin", new BigDecimal("1"))));

        assertThatThrownBy(() -> service.findBySymbol("xrp"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("xrp");
    }

    @Test
    void findBySymbol_blank_throwsIllegalArgument() {
        CryptoMarketService service = newService();
        assertThatThrownBy(() -> service.findBySymbol("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findBySymbol(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- getCoinDetail delegates ----------

    @Test
    void getCoinDetail_delegatesToPort() {
        CryptoMarketService service = newService();
        Map<String, Object> detail = Map.of("id", "bitcoin", "genesis_date", "2009-01-03");
        when(coinGeckoPort.fetchCoinDetail("bitcoin")).thenReturn(detail);

        Map<String, Object> result = service.getCoinDetail("bitcoin");

        assertThat(result).isEqualTo(detail);
        verify(coinGeckoPort).fetchCoinDetail("bitcoin");
    }

    // ---------- getCoinDetail(coinId, lang) — TR açıklama doldurma ----------

    @Test
    void getCoinDetail_langTr_blankTr_translatesEnIntoTr() {
        CryptoMarketService service = newService();
        java.util.Map<String, Object> description = new java.util.HashMap<>();
        description.put("tr", "");
        description.put("en", "RAIN is a token.");
        java.util.Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("description", description);
        when(coinGeckoPort.fetchCoinDetail("rain")).thenReturn(detail);
        when(descriptionTranslationService.translateToTurkish("rain", "RAIN is a token."))
                .thenReturn("RAIN bir token'dır.");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultDesc = (Map<String, Object>) service.getCoinDetail("rain", "tr").get("description");

        assertThat(resultDesc.get("tr")).isEqualTo("RAIN bir token'dır.");
        verify(descriptionTranslationService).translateToTurkish("rain", "RAIN is a token.");
    }

    @Test
    void getCoinDetail_langTr_existingTr_doesNotTranslate() {
        CryptoMarketService service = newService();
        java.util.Map<String, Object> description = new java.util.HashMap<>();
        description.put("tr", "Mevcut Türkçe açıklama.");
        description.put("en", "Existing english.");
        java.util.Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("description", description);
        when(coinGeckoPort.fetchCoinDetail("bitcoin")).thenReturn(detail);

        service.getCoinDetail("bitcoin", "tr");

        verifyNoInteractions(descriptionTranslationService);
    }

    @Test
    void getCoinDetail_langEn_neverTranslates() {
        CryptoMarketService service = newService();
        java.util.Map<String, Object> description = new java.util.HashMap<>();
        description.put("tr", "");
        description.put("en", "Some english.");
        java.util.Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("description", description);
        when(coinGeckoPort.fetchCoinDetail("rain")).thenReturn(detail);

        service.getCoinDetail("rain", "en");

        verifyNoInteractions(descriptionTranslationService);
    }

    // ---------- DTO sanity (15-arg @JsonCreator) ----------

    @Test
    void cryptoMarketItem_getters_reflectConstructorArgs() {
        CryptoMarketItem it = item("bitcoin", "btc", "Bitcoin", new BigDecimal("3000000"));
        assertThat(it.getId()).isEqualTo("bitcoin");
        assertThat(it.getSymbol()).isEqualTo("btc");
        assertThat(it.getName()).isEqualTo("Bitcoin");
        assertThat(it.getImage()).isEqualTo("https://img/bitcoin.png");
        assertThat(it.getCurrentPrice()).isEqualByComparingTo("3000000");
        assertThat(it.getMarketCapRank()).isEqualTo(1);
        assertThat(it.getPriceChangePercentage7d()).isEqualByComparingTo("7.7");
        assertThat(it.getLastUpdated()).isEqualTo("2026-01-01T00:00:00Z");
    }
}
