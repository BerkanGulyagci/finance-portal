package com.finance.portal.common.application.logging;

import com.finance.portal.common.application.logging.model.IntegrationLogEvent;
import com.finance.portal.common.application.logging.port.IntegrationLogPublisherPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CentralIntegrationLogServiceTest {

    @Mock
    private IntegrationLogPublisherPort integrationLogPublisher;

    private CentralIntegrationLogService service;

    @BeforeEach
    void setUp() {
        service = new CentralIntegrationLogService(integrationLogPublisher, null);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publish_populatesStandardIntegrationFieldsAndSanitizesMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/stocks");
        request.setAttribute(RequestLogSupport.ATTR_REQUEST_ID, "req-int-1");
        request.setAttribute(RequestLogSupport.ATTR_TRACE_ID, "trace-int");
        request.setAttribute(RequestLogSupport.ATTR_SPAN_ID, "span-int");
        request.setAttribute(RequestLogSupport.ATTR_CLIENT_IP, "10.0.0.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("symbol", "THYAO");
        metadata.put("apiKey", "must-not-appear");
        metadata.put("trigger", IntegrationLogSupport.TRIGGER_API_REQUEST);

        service.publish(
                IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                "ERROR",
                "External API call failed",
                IntegrationLogSupport.PROVIDER_YAHOO,
                "chart",
                "503",
                120L,
                false,
                true,
                metadata,
                "com.test.Logger"
        );

        ArgumentCaptor<IntegrationLogEvent> captor = ArgumentCaptor.forClass(IntegrationLogEvent.class);
        verify(integrationLogPublisher).publish(captor.capture());
        IntegrationLogEvent event = captor.getValue();

        assertEquals("ERROR", event.getLevel());
        assertEquals(IntegrationLogSupport.CATEGORY, event.getCategory());
        assertEquals(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED, event.getEventType());
        assertEquals(IntegrationLogSupport.SERVICE_NAME, event.getServiceName());
        assertEquals(IntegrationLogSupport.PROVIDER_YAHOO, event.getProvider());
        assertEquals("chart", event.getOperation());
        assertEquals("503", event.getHttpStatus());
        assertEquals(120L, event.getDurationMs());
        assertFalse(event.getFallbackUsed());
        assertTrue(event.getDegraded());
        assertEquals("req-int-1", event.getRequestId());
        assertEquals("trace-int", event.getTraceId());
        assertEquals("span-int", event.getSpanId());
        assertEquals("10.0.0.8", event.getClientIp());
        assertEquals("GET", event.getMethod());
        assertEquals("/api/v1/market/stocks", event.getPath());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getMetadata());
        assertEquals("THYAO", event.getMetadata().get("symbol"));
        assertFalse(event.getMetadata().containsKey("apiKey"));
    }

    @Test
    void inferProviderFromMessage_detectsKnownProviders() {
        assertEquals(IntegrationLogSupport.PROVIDER_YAHOO,
                CentralIntegrationLogService.inferProviderFromMessage("Yahoo Finance API rate limit"));
        assertEquals(IntegrationLogSupport.PROVIDER_COINGECKO,
                CentralIntegrationLogService.inferProviderFromMessage("Crypto market data unavailable"));
        assertEquals(IntegrationLogSupport.PROVIDER_NEWSAPI,
                CentralIntegrationLogService.inferProviderFromMessage("External news API error"));
        assertEquals(IntegrationLogSupport.PROVIDER_RASYONET,
                CentralIntegrationLogService.inferProviderFromMessage("Rasyonet card error"));
        assertEquals(IntegrationLogSupport.PROVIDER_EXTERNAL,
                CentralIntegrationLogService.inferProviderFromMessage(null));
    }

    @Test
    void publish_usesNullMetadataWhenEmptyAfterSanitization() {
        service.publish(
                IntegrationLogSupport.EVENT_NEWS_FETCH_FAILED,
                "ERROR",
                "News fetch failed",
                IntegrationLogSupport.PROVIDER_NEWSAPI,
                "fetch",
                null,
                null,
                null,
                null,
                Map.of("fullUrl", "https://newsapi.org?apiKey=secret"),
                "com.test.Logger"
        );

        ArgumentCaptor<IntegrationLogEvent> captor = ArgumentCaptor.forClass(IntegrationLogEvent.class);
        verify(integrationLogPublisher).publish(captor.capture());
        assertNull(captor.getValue().getMetadata());
    }
}
