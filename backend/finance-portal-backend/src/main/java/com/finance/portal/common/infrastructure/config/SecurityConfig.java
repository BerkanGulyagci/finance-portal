package com.finance.portal.common.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import com.finance.portal.common.application.logging.CentralErrorLogService;
import com.finance.portal.common.application.logging.ErrorLogSupport;
import com.finance.portal.common.infrastructure.security.DisabledAccountFilter;
import com.finance.portal.common.infrastructure.security.EmailVerifiedFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DisabledAccountFilter disabledAccountFilter,
            EmailVerifiedFilter emailVerifiedFilter,
            CentralErrorLogService centralErrorLogService
    ) throws Exception {
        log.info("Custom SecurityFilterChain initialized");
        log.info("/api/v1/market/** is public");
        log.info("/api/v1/portfolios/** requires authentication + verified email");
        log.info("/api/v1/admin/** requires ROLE_ADMIN");
        log.info("/api/v1/me requires authentication (email verification gate exempt for status check)");
        log.info("/api/v1/kafka/test requires authentication");
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // OpenAPI/Swagger UI — herkese açık (API dokümantasyonu)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/mappings").permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/v1/kafka/test").authenticated()
                        .requestMatchers("/api/v1/me", "/api/v1/me/**").authenticated()
                        .requestMatchers("/api/v1/support", "/api/v1/support/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/portfolios", "/api/v1/portfolios/**").authenticated()
                        .requestMatchers("/api/v1/alarms", "/api/v1/alarms/**").authenticated()
                        .requestMatchers("/api/v1/notifications", "/api/v1/notifications/**").authenticated()
                        .requestMatchers("/api/v1/newsletter/unsubscribe").permitAll()
                        .requestMatchers("/api/v1/newsletter", "/api/v1/newsletter/**").authenticated()
                        .requestMatchers("/api/v1/news", "/api/v1/news/**").permitAll()
                        .requestMatchers("/api/v1/assistant", "/api/v1/assistant/**").permitAll()
                        .requestMatchers("/api/v1/auth", "/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/proxy/**").permitAll()
                        .requestMatchers("/api/v1/market/**").permitAll()
                        .requestMatchers("/api/v1/gold/**").permitAll()
                        .requestMatchers("/api/v1/market/assets/**").permitAll()
                        .requestMatchers("/api/v1/proxy/**").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(request -> {
                            // Only attempt JWT validation if Authorization header is present
                            DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
                            return resolver.resolve(request);
                        })
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRealmRoleConverter()))
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            centralErrorLogService.logHandledError(
                                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                                    ErrorLogSupport.EVENT_UNAUTHORIZED_ACCESS,
                                    "Unauthorized access: " + authException.getMessage(),
                                    authException,
                                    SecurityConfig.class.getName());
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            centralErrorLogService.logHandledError(
                                    org.springframework.http.HttpStatus.FORBIDDEN,
                                    ErrorLogSupport.EVENT_ACCESS_DENIED,
                                    "Access denied: " + accessDeniedException.getMessage(),
                                    accessDeniedException,
                                    SecurityConfig.class.getName());
                        })
                )
                .addFilterAfter(disabledAccountFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(emailVerifiedFilter, DisabledAccountFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter keycloakRealmRoleConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<? extends GrantedAuthority> defaultAuthorities =
                    defaultAuthoritiesConverter.convert(jwt);
            Collection<? extends GrantedAuthority> realmRoles = extractRealmRoleAuthorities(jwt);

            java.util.List<GrantedAuthority> combined = new java.util.ArrayList<>();
            if (defaultAuthorities != null) {
                combined.addAll(defaultAuthorities);
            }
            combined.addAll(realmRoles);
            return combined;
        });

        return converter;
    }

    private Collection<? extends GrantedAuthority> extractRealmRoleAuthorities(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaim("realm_access");

        if (!(realmAccessClaim instanceof Map)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> realmAccess = (Map<String, Object>) realmAccessClaim;
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        Collection<String> roles = (Collection<String>) rolesObj;

        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
