package com.finance.portal.common.presentation.filter;

import com.finance.portal.common.infrastructure.logging.RequestLogEvent;
import com.finance.portal.common.infrastructure.logging.RequestLogKafkaPublisher;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
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
 * - MDC'ye requestId, method, path, userId, status, durationMs koyar
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

    private final RequestLogKafkaPublisher kafkaPublisher;

    // Constructor injection — @Autowired(required=false) field injection filter'larda güvenilmez
    public RequestLoggingFilter(@Autowired(required = false) RequestLogKafkaPublisher kafkaPublisher) {
        this.kafkaPublisher = kafkaPublisher;
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
        long startNanos = System.nanoTime(); // negatif durationMs sorununu önler
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // OTel span'ı doFilter öncesi yakala — agent bu noktada span'ı başlatmış olabilir
        // Eğer henüz başlatmamışsa doFilter sonrası finally'de MDC'den okuruz
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

        try {
            ThreadContext.put("requestId", requestId);
            ThreadContext.put("method", request.getMethod());
            ThreadContext.put("path", request.getRequestURI());

            String userId = extractUserId();
            if (userId != null) {
                ThreadContext.put("userId", userId);
            }

            filterChain.doFilter(request, response);

        } finally {
            long durationMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            int httpStatus = response.getStatus();
            String userId = ThreadContext.get("userId");

            // Önce Span.current() ile yakaladığımız değeri kullan
            // Yoksa OTel agent'ın MDC'ye yazdığı trace_id/span_id key'lerini dene
            String traceId = capturedTraceId;
            String spanId  = capturedSpanId;

            if (traceId == null || traceId.isBlank()) {
                // OTel Java Agent 2.x Log4j2 MDC'ye trace_id (underscore) ile yazar
                traceId = ThreadContext.get("trace_id");
            }
            if (spanId == null || spanId.isBlank()) {
                spanId = ThreadContext.get("span_id");
            }

            // Span.current() doFilter sonrası da dene (agent span'ı response yazıldıktan sonra kapatır)
            if (traceId == null || traceId.isBlank()) {
                try {
                    SpanContext spanCtx = Span.current().getSpanContext();
                    if (spanCtx.isValid()) {
                        traceId = spanCtx.getTraceId();
                        spanId  = spanCtx.getSpanId();
                    }
                } catch (Throwable ignored) {}
            }

            // camelCase alias'ları MDC'ye koy — log4j2-json-template.json her iki key'i de okur
            if (traceId != null && !traceId.isBlank()) ThreadContext.put("traceId", traceId);
            if (spanId  != null && !spanId.isBlank())  ThreadContext.put("spanId",  spanId);

            ThreadContext.put("status", String.valueOf(httpStatus));
            ThreadContext.put("durationMs", String.valueOf(durationMs));

            // Console JSON log — Aşama 1'den gelen yapı, değişmez
            String message = String.format("HTTP %s %s -> %d (%dms)",
                    request.getMethod(), request.getRequestURI(), httpStatus, durationMs);
            log.info(message);

            // Kafka'ya async/best-effort gönder
            if (kafkaPublisher != null) {
                try {
                    RequestLogEvent event = RequestLogEvent.builder()
                            .timestamp(ISO_FMT.format(Instant.ofEpochMilli(startTime)))
                            .serviceName(SERVICE_NAME)
                            .level("INFO")
                            .logger(RequestLoggingFilter.class.getName())
                            .message(message)
                            .requestId(requestId)
                            .method(request.getMethod())
                            .path(request.getRequestURI())
                            .status(String.valueOf(httpStatus))
                            .durationMs(String.valueOf(durationMs))
                            .userId(userId)
                            .traceId(traceId)
                            .spanId(spanId)
                            .build();
                    kafkaPublisher.publish(event);
                } catch (Exception e) {
                    // Kafka gönderimi hiçbir zaman filter'ı crash etmemeli
                    System.err.println("[RequestLoggingFilter] Kafka publish error: " + e.getMessage());
                }
            }

            // MDC temizle — thread pool sızıntısı önlenir
            ThreadContext.clearAll();
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
