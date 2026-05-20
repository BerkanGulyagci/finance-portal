package com.finance.portal.common.application.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorLogContextTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void fromCurrentRequest_readsRequestAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.setAttribute(RequestLogSupport.ATTR_REQUEST_ID, "abc123");
        request.setAttribute(RequestLogSupport.ATTR_TRACE_ID, "trace-1");
        request.setAttribute(RequestLogSupport.ATTR_SPAN_ID, "span-1");
        request.setAttribute(RequestLogSupport.ATTR_CLIENT_IP, "203.0.113.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ErrorLogContext ctx = ErrorLogContext.fromCurrentRequest();

        assertEquals("abc123", ctx.requestId());
        assertEquals("trace-1", ctx.traceId());
        assertEquals("span-1", ctx.spanId());
        assertEquals("203.0.113.1", ctx.clientIp());
        assertEquals("GET", ctx.method());
        assertEquals("/api/admin/users", ctx.path());
    }
}
