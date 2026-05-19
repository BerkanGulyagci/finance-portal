package com.finance.portal.common.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailVerifiedFilterTest {

    @Test
    void shouldAcceptTrueBooleanClaim() {
        Jwt jwt = jwtWithEmailVerified(true);
        assertTrue(EmailVerifiedFilter.isEmailVerified(jwt));
    }

    @Test
    void shouldRejectFalseBooleanClaim() {
        Jwt jwt = jwtWithEmailVerified(false);
        assertFalse(EmailVerifiedFilter.isEmailVerified(jwt));
    }

    @Test
    void shouldRejectMissingClaim() {
        Jwt jwt = jwtWithEmailVerified(null);
        assertFalse(EmailVerifiedFilter.isEmailVerified(jwt));
    }

    @Test
    void shouldParseStringTrueClaim() {
        Jwt jwt = jwt(Map.of("email_verified", "true"));
        assertTrue(EmailVerifiedFilter.isEmailVerified(jwt));
    }

    private static Jwt jwtWithEmailVerified(Boolean emailVerified) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", "test-user");
        if (emailVerified != null) {
            claims.put("email_verified", emailVerified);
        }
        return jwt(claims);
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                claims
        );
    }
}
