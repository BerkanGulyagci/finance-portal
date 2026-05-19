package com.finance.portal.common.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.common.presentation.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DisabledAccountFilter extends OncePerRequestFilter {

    public static final String ACCOUNT_DISABLED_MESSAGE = "ACCOUNT_DISABLED: Hesabınız devre dışı bırakıldı.";

    private final UserAccountStatusPort userAccountStatusPort;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getSubject();
            if (userId != null && !userAccountStatusPort.isAccountEnabled(userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(
                        response.getOutputStream(),
                        ApiResponse.error(ACCOUNT_DISABLED_MESSAGE)
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
