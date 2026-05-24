package com.finance.portal.common.application.logging;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public record ErrorLogContext(
        String requestId,
        String traceId,
        String spanId,
        String clientIp,
        String userId,
        String method,
        String path
) {
    public static ErrorLogContext fromCurrentRequest() {
        // traceId/spanId'yi aktif OpenTelemetry span'inden al — hem HTTP isteğinde
        // hem de @Scheduled görevlerde (digest/alarm değerlendirme) geçerlidir.
        String[] tr = currentTrace();
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            // İstek yok (zamanlanmış iş) — yine de trace context'i taşı
            return new ErrorLogContext(null, tr[0], tr[1], null, extractUserId(), null, null);
        }
        HttpServletRequest request = attrs.getRequest();
        String clientIp = (String) request.getAttribute(RequestLogSupport.ATTR_CLIENT_IP);
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = RequestLogSupport.extractClientIp(request);
        }
        String traceId = tr[0] != null ? tr[0] : (String) request.getAttribute(RequestLogSupport.ATTR_TRACE_ID);
        String spanId = tr[1] != null ? tr[1] : (String) request.getAttribute(RequestLogSupport.ATTR_SPAN_ID);
        return new ErrorLogContext(
                (String) request.getAttribute(RequestLogSupport.ATTR_REQUEST_ID),
                traceId,
                spanId,
                clientIp,
                extractUserId(),
                request.getMethod(),
                request.getRequestURI()
        );
    }

    public static ErrorLogContext empty() {
        return new ErrorLogContext(null, null, null, null, null, null, null);
    }

    /** Aktif OTel span'inden [traceId, spanId]; geçerli değilse [null, null]. */
    private static String[] currentTrace() {
        try {
            SpanContext sc = Span.current().getSpanContext();
            if (sc.isValid()) {
                return new String[]{sc.getTraceId(), sc.getSpanId()};
            }
        } catch (Exception ignored) {
            // OTel yoksa sessizce geç
        }
        return new String[]{null, null};
    }

    private static String extractUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return null;
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof Jwt jwt) {
                String username = jwt.getClaimAsString("preferred_username");
                if (username != null && !username.isBlank()) {
                    return username;
                }
                return jwt.getSubject();
            }
            String name = auth.getName();
            return "anonymousUser".equals(name) ? null : name;
        } catch (Exception e) {
            return null;
        }
    }
}
