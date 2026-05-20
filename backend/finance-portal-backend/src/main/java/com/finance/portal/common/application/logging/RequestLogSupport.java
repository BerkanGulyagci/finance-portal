package com.finance.portal.common.application.logging;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Shared helpers for HTTP request log events (console MDC + Kafka payload).
 */
public final class RequestLogSupport {

    public static final String CATEGORY = "REQUEST";
    public static final String EVENT_TYPE = "HTTP_REQUEST_COMPLETED";

    /** Request attributes — error handler filter finally sonrası okur. */
    public static final String ATTR_REQUEST_ID = "log.requestId";
    public static final String ATTR_TRACE_ID = "log.traceId";
    public static final String ATTR_SPAN_ID = "log.spanId";
    public static final String ATTR_CLIENT_IP = "log.clientIp";

    private RequestLogSupport() {}

    public static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    public static int resolveHttpStatus(int responseStatus, Throwable requestException) {
        if (requestException != null) {
            return 500;
        }
        return responseStatus;
    }

    public static String resolveLevelName(int httpStatus, Throwable requestException) {
        if (requestException != null || httpStatus >= 500) {
            return "ERROR";
        }
        if (httpStatus >= 400) {
            return "WARN";
        }
        return "INFO";
    }

    /**
     * Compact exception summary for Kafka/OpenSearch (no stack trace).
     */
    public static String formatExceptionSummary(Throwable t) {
        if (t == null) {
            return null;
        }
        String message = t.getMessage();
        if (message == null || message.isBlank()) {
            return t.getClass().getName();
        }
        return t.getClass().getName() + ": " + message;
    }
}
