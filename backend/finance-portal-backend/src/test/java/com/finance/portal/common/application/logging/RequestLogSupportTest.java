package com.finance.portal.common.application.logging;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestLogSupportTest {

    @Test
    void resolveLevelName_infoFor2xx() {
        assertEquals("INFO", RequestLogSupport.resolveLevelName(200, null));
        assertEquals("INFO", RequestLogSupport.resolveLevelName(399, null));
    }

    @Test
    void resolveLevelName_warnFor4xx() {
        assertEquals("WARN", RequestLogSupport.resolveLevelName(404, null));
        assertEquals("WARN", RequestLogSupport.resolveLevelName(499, null));
    }

    @Test
    void resolveLevelName_errorFor5xx() {
        assertEquals("ERROR", RequestLogSupport.resolveLevelName(500, null));
        assertEquals("ERROR", RequestLogSupport.resolveLevelName(503, null));
    }

    @Test
    void resolveLevelName_errorWhenExceptionCaught() {
        assertEquals("ERROR", RequestLogSupport.resolveLevelName(200, new RuntimeException("boom")));
    }

    @Test
    void resolveHttpStatus_500WhenExceptionCaught() {
        assertEquals(500, RequestLogSupport.resolveHttpStatus(200, new RuntimeException("boom")));
    }

    @Test
    void resolveHttpStatus_usesResponseWhenNoException() {
        assertEquals(404, RequestLogSupport.resolveHttpStatus(404, null));
    }

    @Test
    void extractClientIp_prefersXForwardedForFirstIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        assertEquals("203.0.113.1", RequestLogSupport.extractClientIp(request));
    }

    @Test
    void extractClientIp_fallsBackToXRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.2");
        assertEquals("198.51.100.2", RequestLogSupport.extractClientIp(request));
    }

    @Test
    void extractClientIp_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        assertEquals("127.0.0.1", RequestLogSupport.extractClientIp(request));
    }

    @Test
    void formatExceptionSummary_nullWhenNoException() {
        assertNull(RequestLogSupport.formatExceptionSummary(null));
    }

    @Test
    void formatExceptionSummary_classAndMessage() {
        assertEquals(
                "java.lang.IllegalStateException: bad state",
                RequestLogSupport.formatExceptionSummary(new IllegalStateException("bad state")));
    }
}
