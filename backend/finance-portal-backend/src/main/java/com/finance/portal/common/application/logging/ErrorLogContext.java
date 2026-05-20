package com.finance.portal.common.application.logging;

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
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return empty();
        }
        HttpServletRequest request = attrs.getRequest();
        String clientIp = (String) request.getAttribute(RequestLogSupport.ATTR_CLIENT_IP);
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = RequestLogSupport.extractClientIp(request);
        }
        return new ErrorLogContext(
                (String) request.getAttribute(RequestLogSupport.ATTR_REQUEST_ID),
                (String) request.getAttribute(RequestLogSupport.ATTR_TRACE_ID),
                (String) request.getAttribute(RequestLogSupport.ATTR_SPAN_ID),
                clientIp,
                extractUserId(),
                request.getMethod(),
                request.getRequestURI()
        );
    }

    public static ErrorLogContext empty() {
        return new ErrorLogContext(null, null, null, null, null, null, null);
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
