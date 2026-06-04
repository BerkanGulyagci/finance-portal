package com.finance.portal.common.application.logging;

import com.finance.portal.common.application.logging.model.ErrorLogEvent;
import com.finance.portal.common.application.logging.port.ErrorLogPublisherPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CentralErrorLogServiceTest {

    @Mock
    private ErrorLogPublisherPort errorLogPublisher;

    private CentralErrorLogService service;

    @BeforeEach
    void setUp() {
        service = new CentralErrorLogService(errorLogPublisher);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publishValidationError_populatesStandardFields() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setAttribute(RequestLogSupport.ATTR_REQUEST_ID, "req-123");
        request.setAttribute(RequestLogSupport.ATTR_CLIENT_IP, "10.0.0.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        IllegalArgumentException ex = new IllegalArgumentException("bad input");
        service.logHandledError(
                HttpStatus.BAD_REQUEST,
                ErrorLogSupport.EVENT_VALIDATION_FAILED,
                "Validation failed",
                ex);

        ArgumentCaptor<ErrorLogEvent> captor = ArgumentCaptor.forClass(ErrorLogEvent.class);
        verify(errorLogPublisher).publish(captor.capture());
        ErrorLogEvent event = captor.getValue();

        assertEquals("WARN", event.getLevel());
        assertEquals(ErrorLogSupport.CATEGORY, event.getCategory());
        assertEquals(ErrorLogSupport.EVENT_VALIDATION_FAILED, event.getEventType());
        assertEquals("400", event.getStatus());
        assertEquals("req-123", event.getRequestId());
        assertEquals("10.0.0.5", event.getClientIp());
        assertEquals("POST", event.getMethod());
        assertEquals("/api/v1/auth/register", event.getPath());
        assertEquals("java.lang.IllegalArgumentException: bad input", event.getException());
        assertNotNull(event.getTimestamp());
        assertNull(event.getDurationMs());
    }

    @Test
    void publishGlobalException_usesErrorLevel() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/unknown");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        service.logHandledError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorLogSupport.EVENT_GLOBAL_EXCEPTION_HANDLED,
                "Unexpected error",
                new RuntimeException("boom"));

        ArgumentCaptor<ErrorLogEvent> captor = ArgumentCaptor.forClass(ErrorLogEvent.class);
        verify(errorLogPublisher).publish(captor.capture());
        ErrorLogEvent event = captor.getValue();

        assertEquals("ERROR", event.getLevel());
        assertEquals(ErrorLogSupport.EVENT_GLOBAL_EXCEPTION_HANDLED, event.getEventType());
        assertEquals("500", event.getStatus());
    }
}
