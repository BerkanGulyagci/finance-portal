package com.finance.portal.common.application.logging;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.model.IntegrationLogEvent;
import com.finance.portal.common.application.logging.port.IntegrationLogPublisherPort;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.stock.StockCacheWarmupService;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSymbolProvider;
import com.finance.portal.news.infrastructure.external.BloombergHtRssClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationLoggingScenarioTest {

    @Mock
    private IntegrationLogPublisherPort integrationLogPublisher;

    private CentralIntegrationLogService integrationLogService;

    @BeforeEach
    void setUp() {
        integrationLogService = new CentralIntegrationLogService(integrationLogPublisher, null);
    }

    @Test
    void cryptoMarketService_staleCacheFallback_publishesFallbackUsedWarn() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(cache);
        when(cache.get("try:p1:s10")).thenReturn(wrapper);

        CryptoMarketItem cachedItem = new CryptoMarketItem(
                "bitcoin", "btc", "Bitcoin", null,
                BigDecimal.TEN, null, null, null, null, null, null, null, null, null, null);
        when(wrapper.get()).thenReturn(List.of(cachedItem));

        CryptoMarketService service = new CryptoMarketService(null, cacheManager, integrationLogService, null, null);

        List<CryptoMarketItem> result = service.getCryptosFallback(1, 10, new RuntimeException("upstream fail"));

        assertEquals(1, result.size());
        ArgumentCaptor<IntegrationLogEvent> captor = ArgumentCaptor.forClass(IntegrationLogEvent.class);
        verify(integrationLogPublisher).publish(captor.capture());
        IntegrationLogEvent event = captor.getValue();
        assertEquals(IntegrationLogSupport.EVENT_EXTERNAL_API_FALLBACK_USED, event.getEventType());
        assertEquals("WARN", event.getLevel());
        assertEquals(IntegrationLogSupport.PROVIDER_COINGECKO, event.getProvider());
    }

    @Test
    void cryptoMarketService_noCache_rethrowsExternalApiException() {
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache("cryptoMarketsCache")).thenReturn(null);

        CryptoMarketService service = new CryptoMarketService(null, cacheManager, integrationLogService, null, null);

        assertThrows(ExternalApiException.class,
                () -> service.getCryptosFallback(1, 10, new RuntimeException("upstream fail")));
        verify(integrationLogPublisher, times(0)).publish(any());
    }

    @Test
    void stockCacheWarmupService_partialFailure_publishesSchedulerJobFailedWarn() {
        StockQueryService stockQueryService = mock(StockQueryService.class);
        StockSymbolProvider stockSymbolProvider = mock(StockSymbolProvider.class);
        com.finance.portal.market.application.index.IndexQueryService indexQueryService =
                mock(com.finance.portal.market.application.index.IndexQueryService.class);
        when(indexQueryService.getIndices()).thenReturn(java.util.Collections.emptyList());
        when(stockSymbolProvider.getTotalElements()).thenReturn(40);
        doThrow(new RuntimeException("yahoo fail")).when(stockQueryService).getPagedStockSummaries(eq(0), eq(20));

        StockCacheWarmupService service = new StockCacheWarmupService(
                stockQueryService, stockSymbolProvider, indexQueryService, integrationLogService);
        service.scheduledWarmup();

        ArgumentCaptor<IntegrationLogEvent> captor = ArgumentCaptor.forClass(IntegrationLogEvent.class);
        verify(integrationLogPublisher).publish(captor.capture());
        IntegrationLogEvent event = captor.getValue();
        assertEquals(IntegrationLogSupport.EVENT_SCHEDULER_JOB_FAILED, event.getEventType());
        assertEquals("WARN", event.getLevel());
        assertEquals(1, event.getMetadata().get("failedCount"));
        assertEquals(2, event.getMetadata().get("totalCount"));
    }

    @Test
    void bloombergRssClient_fetchFailure_publishesNewsFetchFailed() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(any(), eq(byte[].class))).thenThrow(new RuntimeException("network down"));

        BloombergHtRssClient client = new BloombergHtRssClient(restTemplate, integrationLogService);
        List<?> articles = client.fetchNews();

        assertEquals(0, articles.size());
        ArgumentCaptor<IntegrationLogEvent> captor = ArgumentCaptor.forClass(IntegrationLogEvent.class);
        verify(integrationLogPublisher).publish(captor.capture());
        IntegrationLogEvent event = captor.getValue();
        assertEquals(IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED, event.getEventType());
        assertEquals(IntegrationLogSupport.PROVIDER_BLOOMBERG_HT, event.getProvider());
    }
}
