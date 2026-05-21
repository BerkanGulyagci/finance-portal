package com.finance.portal.common.presentation.filter;

import com.finance.portal.common.application.logging.RequestLogSupport;
import com.finance.portal.common.application.logging.model.RequestLogEvent;
import com.finance.portal.common.application.logging.port.RequestLogPublisherPort;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Her HTTP isteği için:
 * - requestId (UUID) üretir
 * - MDC'ye requestId, method, path, userId, clientIp, status, durationMs koyar
 * - Console'a JSON log atar (Log4j2 + JsonTemplateLayout)
 * - Kafka'ya RequestLogEvent gönderir (async/best-effort)
 * - Request/response body loglanmaz
 * - finally bloğunda MDC temizlenir
 */
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LogManager.getLogger(RequestLoggingFilter.class);
    private static final String SERVICE_NAME = "finance-portal-backend";
    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final String ACTUATOR_PREFIX = "/actuator";

    private final RequestLogPublisherPort requestLogPublisher;

    public RequestLoggingFilter(@Autowired(required = false) RequestLogPublisherPort requestLogPublisher) {
        this.requestLogPublisher = requestLogPublisher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ACTUATOR_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        long startNanos = System.nanoTime();
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String clientIp = RequestLogSupport.extractClientIp(request);
        request.setAttribute(RequestLogSupport.ATTR_REQUEST_ID, requestId);
        request.setAttribute(RequestLogSupport.ATTR_CLIENT_IP, clientIp);

        String capturedTraceId = null;
        String capturedSpanId  = null;
        try {
            SpanContext spanCtx = Span.current().getSpanContext();
            if (spanCtx.isValid()) {
                capturedTraceId = spanCtx.getTraceId();
                capturedSpanId  = spanCtx.getSpanId();
            }
        } catch (Throwable ignored) {
            // OTel API classpath'te yoksa veya agent yüklü değilse sessizce geç
        }

        Throwable requestException = null;
        try {
            ThreadContext.put("requestId", requestId);
            ThreadContext.put("method", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());
            ThreadContext.put("clientIp", clientIp);
            ThreadContext.put("category", RequestLogSupport.CATEGORY);
            ThreadContext.put("eventType", RequestLogSupport.EVENT_TYPE);

            String userId = extractUserId();
            if (userId != null) {
                ThreadContext.put("userId", userId);
            }

            // Enrich root HTTP span with business context for end-to-end debugging
            try {
                Span span = Span.current();
                if (span != null && span.getSpanContext().isValid()) {
                    span.setAttribute("app.request_id", requestId);
                    span.setAttribute("app.client_ip", clientIp);
                    if (userId != null) {
                        span.setAttribute("enduser.id", userId);
                    }
                }
            } catch (Throwable ignored) {}

            filterChain.doFilter(request, response);

        } catch (Throwable t) {
            requestException = t;
            // Record exception on the active span so traces show error details
            try {
                Span span = Span.current();
                if (span != null && span.getSpanContext().isValid()) {
                    span.recordException(t);
                    span.setStatus(StatusCode.ERROR, t.getClass().getSimpleName());
                }
            } catch (Throwable ignored) {}
            throw t;
        } finally {
            long durationMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            int httpStatus = RequestLogSupport.resolveHttpStatus(response.getStatus(), requestException);
            String userId = ThreadContext.get("userId");
            String level = RequestLogSupport.resolveLevelName(httpStatus, requestException);
            String exceptionSummary = RequestLogSupport.formatExceptionSummary(requestException);

            String traceId = capturedTraceId;
            String spanId  = capturedSpanId;

            if (traceId == null || traceId.isBlank()) {
                traceId = ThreadContext.get("trace_id");
            }
            if (spanId == null || spanId.isBlank()) {
                spanId = ThreadContext.get("span_id");
            }

            if (traceId == null || traceId.isBlank()) {
                try {
                    SpanContext spanCtx = Span.current().getSpanContext();
                    if (spanCtx.isValid()) {
                        traceId = spanCtx.getTraceId();
                        spanId  = spanCtx.getSpanId();
                    }
                } catch (Throwable ignored) {}
            }

            if (traceId != null && !traceId.isBlank()) ThreadContext.put("traceId", traceId);
            if (spanId  != null && !spanId.isBlank())  ThreadContext.put("spanId",  spanId);

            if (traceId != null && !traceId.isBlank()) {
                request.setAttribute(RequestLogSupport.ATTR_TRACE_ID, traceId);
            }
            if (spanId != null && !spanId.isBlank()) {
                request.setAttribute(RequestLogSupport.ATTR_SPAN_ID, spanId);
            }

            ThreadContext.put("status", String.valueOf(httpStatus));
            ThreadContext.put("durationMs", String.valueOf(durationMs));

            String message = String.format("HTTP %s %s -> %d (%dms)",
                    request.getMethod(), request.getRequestURI(), httpStatus, durationMs);

            logAtLevel(level, message, requestException);

            if (requestLogPublisher != null) {
                try {
                    RequestLogEvent event = RequestLogEvent.builder()
                            .timestamp(ISO_FMT.format(Instant.ofEpochMilli(startTime)))
                            .level(level)
                            .serviceName(SERVICE_NAME)
                            .category(RequestLogSupport.CATEGORY)
                            .eventType(RequestLogSupport.EVENT_TYPE)
                            .message(message)
                            .exception(exceptionSummary)
                            .requestId(requestId)
                            .method(request.getMethod())
                            .path(request.getRequestURI())
                            .status(String.valueOf(httpStatus))
                            .durationMs(String.valueOf(durationMs))
                            .userId(userId)
                            .clientIp(clientIp)
                            .traceId(traceId)
                            .spanId(spanId)
                            .logger(RequestLoggingFilter.class.getName())
                            .build();
                    requestLogPublisher.publish(event);
                } catch (Exception e) {
                    System.err.println("[RequestLoggingFilter] Kafka publish error: " + e.getMessage());
                }
            }

            ThreadContext.clearAll();
        }
    }

    private void logAtLevel(String level, String message, Throwable requestException) {
        switch (level) {
            case "ERROR" -> {
                if (requestException != null) {
                    log.error(message, requestException);
                } else {
                    log.error(message);
                }
            }
            case "WARN" -> log.warn(message);
            default -> log.info(message);
        }
    }

    private String extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            Object principal = auth.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String username = jwt.getClaimAsString("preferred_username");
                if (username != null && !username.isBlank()) return username;
                return jwt.getSubject();
            }
            String name = auth.getName();
            return "anonymousUser".equals(name) ? null : name;
        } catch (Exception e) {
            return null;
        }
    }
}
