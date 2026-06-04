package com.finance.portal.common.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.presentation.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EmailVerifiedFilter extends OncePerRequestFilter {

    public static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "EMAIL_NOT_VERIFIED: Email adresinizi doğrulamanız gerekiyor.";

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/api/v1/auth",
            "/api/v1/me",
            "/actuator",
            "/api/v1/market",
            "/api/v1/news",
            "/api/v1/gold",
            "/api/v1/proxy",
            "/api/v1/health"
    );

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            if (!isEmailVerified(jwt)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(
                        response.getOutputStream(),
                        ApiResponse.error(EMAIL_NOT_VERIFIED_MESSAGE)
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    static boolean isEmailVerified(Jwt jwt) {
        Object claim = jwt.getClaim("email_verified");
        if (claim instanceof Boolean bool) {
            return bool;
        }
        if (claim instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        return false;
    }
}
