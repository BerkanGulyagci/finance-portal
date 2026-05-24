package com.finance.portal.common.application.logging;

import com.finance.portal.common.application.logging.model.BusinessLogEvent;
import com.finance.portal.common.application.logging.port.BusinessLogPublisherPort;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CentralBusinessLogServiceTest {

    @Mock
    private BusinessLogPublisherPort businessLogPublisher;

    private CentralBusinessLogService service;

    @BeforeEach
    void setUp() {
        service = new CentralBusinessLogService(businessLogPublisher, null);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publish_populatesStandardFieldsAndSanitizesMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/portfolios");
        request.setAttribute(RequestLogSupport.ATTR_REQUEST_ID, "req-456");
        request.setAttribute(RequestLogSupport.ATTR_TRACE_ID, "trace-1");
        request.setAttribute(RequestLogSupport.ATTR_SPAN_ID, "span-2");
        request.setAttribute(RequestLogSupport.ATTR_CLIENT_IP, "192.168.1.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portfolioId", "p-1");
        metadata.put("password", "must-not-appear");
        metadata.put("changedFields", List.of("firstName", "lastName"));

        service.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_PROFILE_UPDATED,
                "INFO",
                "Profile updated",
                "USER",
                "user-1",
                BusinessLogSupport.ACTION_UPDATE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                "user-1",
                "com.test.Logger"
        );

        ArgumentCaptor<BusinessLogEvent> captor = ArgumentCaptor.forClass(BusinessLogEvent.class);
        verify(businessLogPublisher).publish(captor.capture());
        BusinessLogEvent event = captor.getValue();

        assertEquals("INFO", event.getLevel());
        assertEquals(BusinessLogSupport.CATEGORY_AUDIT, event.getCategory());
        assertEquals(BusinessLogSupport.EVENT_PROFILE_UPDATED, event.getEventType());
        assertEquals(BusinessLogSupport.SERVICE_NAME, event.getServiceName());
        assertEquals("req-456", event.getRequestId());
        assertEquals("trace-1", event.getTraceId());
        assertEquals("span-2", event.getSpanId());
        assertEquals("192.168.1.10", event.getClientIp());
        assertEquals("POST", event.getMethod());
        assertEquals("/api/portfolios", event.getPath());
        assertEquals("user-1", event.getUserId());
        assertEquals("USER", event.getEntityType());
        assertEquals("user-1", event.getEntityId());
        assertEquals(BusinessLogSupport.ACTION_UPDATE, event.getAction());
        assertEquals(BusinessLogSupport.RESULT_SUCCESS, event.getResult());
        assertNotNull(event.getTimestamp());
        assertNotNull(event.getMetadata());
        assertEquals("p-1", event.getMetadata().get("portfolioId"));
        assertFalseMetadataContainsPassword(event.getMetadata());
    }

    @Test
    void publish_usesNullMetadataWhenEmptyAfterSanitization() {
        service.publish(
                BusinessLogSupport.CATEGORY_BUSINESS,
                BusinessLogSupport.EVENT_PORTFOLIO_DELETED,
                "WARN",
                "Portfolio deleted",
                "PORTFOLIO",
                "p-99",
                BusinessLogSupport.ACTION_DELETE,
                BusinessLogSupport.RESULT_SUCCESS,
                Map.of("email", "secret@example.com"),
                "actor-1",
                "com.test.Logger"
        );

        ArgumentCaptor<BusinessLogEvent> captor = ArgumentCaptor.forClass(BusinessLogEvent.class);
        verify(businessLogPublisher).publish(captor.capture());
        assertNull(captor.getValue().getMetadata());
    }

    @Test
    void publish_setsCorrectLevelForWarnAuditEvent() {
        service.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_USER_BANNED,
                "WARN",
                "User banned",
                "USER",
                "target-1",
                BusinessLogSupport.ACTION_BAN,
                BusinessLogSupport.RESULT_SUCCESS,
                Map.of("targetUserId", "target-1", "adminUserId", "admin-1", "banType", "PERMANENT"),
                "admin-1",
                "com.test.Logger"
        );

        ArgumentCaptor<BusinessLogEvent> captor = ArgumentCaptor.forClass(BusinessLogEvent.class);
        verify(businessLogPublisher).publish(captor.capture());
        BusinessLogEvent event = captor.getValue();

        assertEquals("WARN", event.getLevel());
        assertEquals(BusinessLogSupport.EVENT_USER_BANNED, event.getEventType());
        assertEquals(BusinessLogSupport.CATEGORY_AUDIT, event.getCategory());
    }

    private static void assertFalseMetadataContainsPassword(Map<String, Object> metadata) {
        org.junit.jupiter.api.Assertions.assertFalse(metadata.containsKey("password"));
    }
}
