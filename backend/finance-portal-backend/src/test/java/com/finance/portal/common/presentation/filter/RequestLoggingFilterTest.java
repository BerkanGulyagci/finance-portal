package com.finance.portal.common.presentation.filter;

import com.finance.portal.common.application.logging.RequestLogSupport;
import com.finance.portal.common.application.logging.model.RequestLogEvent;
import com.finance.portal.common.application.logging.port.RequestLogPublisherPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Branch-coverage focused tests for {@link RequestLoggingFilter}.
 * Exercises: shouldNotFilter actuator arm, INFO/WARN/ERROR status levels,
 * exception passthrough, null vs present publisher, publisher throwing,
 * and the userId extraction arms (anonymous / non-anonymous / Jwt /
 * blank preferred_username fallback / unauthenticated / null auth).
 */
class RequestLoggingFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------------
    // shouldNotFilter — actuator prefix branch (true) and normal path (false)
    // ---------------------------------------------------------------------

    @Test
    void shouldNotFilter_returnsTrueForActuatorPath() throws ServletException {
        RequestLoggingFilter filter = new RequestLoggingFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_returnsFalseForApplicationPath() throws ServletException {
        RequestLoggingFilter filter = new RequestLoggingFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portfolios");
        assertFalse(filter.shouldNotFilter(request));
    }

    // ---------------------------------------------------------------------
    // INFO branch: 2xx status, no exception, null publisher path
    // ---------------------------------------------------------------------

    @Test
    void doFilter_infoLevel_nullPublisher_doesNotPublishAndChainInvoked() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // chain ran -> request stored as filterChain's last request
        assertThat(chain.getRequest()).isSameAs(request);
        // request id attribute set
        assertThat(request.getAttribute(RequestLogSupport.ATTR_REQUEST_ID)).isNotNull();
        assertThat(request.getAttribute(RequestLogSupport.ATTR_CLIENT_IP)).isNotNull();
    }

    // ---------------------------------------------------------------------
    // WARN branch: 4xx status (>=400, <500), publisher present
    // ---------------------------------------------------------------------

    @Test
    void doFilter_warnLevel_4xx_publishesWarnEvent() throws Exception {
        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        RequestLogEvent event = captor.getValue();
        assertThat(event.getLevel()).isEqualTo("WARN");
        assertThat(event.getStatus()).isEqualTo("404");
        assertThat(event.getMethod()).isEqualTo("POST");
        assertThat(event.getPath()).isEqualTo("/api/login");
        assertThat(event.getException()).isNull();
    }

    // ---------------------------------------------------------------------
    // ERROR branch via 5xx status (no exception)
    // ---------------------------------------------------------------------

    @Test
    void doFilter_errorLevel_5xxStatus_publishesErrorEvent() throws Exception {
        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(503);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getLevel()).isEqualTo("ERROR");
        assertThat(captor.getValue().getStatus()).isEqualTo("503");
        assertThat(captor.getValue().getException()).isNull();
    }

    // ---------------------------------------------------------------------
    // ERROR branch via thrown exception: passthrough + status forced to 500
    // + exception summary populated + chain exception rethrown
    // ---------------------------------------------------------------------

    @Test
    void doFilter_chainThrows_rethrowsAndPublishesErrorWith500() throws Exception {
        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/explode");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200); // would be INFO, but exception forces 500/ERROR

        RuntimeException boom = new IllegalStateException("kaboom");
        FilterChain throwingChain = (req, res) -> { throw boom; };

        assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
                .isSameAs(boom);

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        RequestLogEvent event = captor.getValue();
        assertThat(event.getLevel()).isEqualTo("ERROR");
        assertThat(event.getStatus()).isEqualTo("500");
        assertThat(event.getException()).contains("IllegalStateException").contains("kaboom");
    }

    // ---------------------------------------------------------------------
    // Publisher itself throws -> swallowed (catch branch), chain still ok
    // ---------------------------------------------------------------------

    @Test
    void doFilter_publisherThrows_isSwallowed() throws Exception {
        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        doThrow(new RuntimeException("kafka down")).when(publisher).publish(any());
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ok");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        MockFilterChain chain = new MockFilterChain();

        // Must NOT propagate the publisher exception
        filter.doFilter(request, response, chain);

        verify(publisher).publish(any());
        assertThat(chain.getRequest()).isSameAs(request);
    }

    // ---------------------------------------------------------------------
    // extractUserId arms (exercised end-to-end via the filter)
    // ---------------------------------------------------------------------

    @Test
    void doFilter_nullAuthentication_noUserId() throws Exception {
        SecurityContextHolder.clearContext(); // auth == null
        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void doFilter_anonymousAuthentication_noUserId() throws Exception {
        Authentication anon = new AnonymousAuthenticationToken(
                "key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anon);

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        // name == "anonymousUser" -> null
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void doFilter_namedPrincipal_setsUserId() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "alice", "pw", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("alice");
    }

    @Test
    void doFilter_jwtPrincipal_withPreferredUsername_usesIt() throws Exception {
        Jwt jwt = jwt(claims("sub-123", "bob"));
        Authentication auth = new UsernamePasswordAuthenticationToken(
                jwt, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("bob");
    }

    @Test
    void doFilter_jwtPrincipal_blankPreferredUsername_fallsBackToSubject() throws Exception {
        // preferred_username present but blank -> fall back to subject
        Jwt jwt = jwt(claims("subject-xyz", "   "));
        Authentication auth = new UsernamePasswordAuthenticationToken(
                jwt, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("subject-xyz");
    }

    @Test
    void doFilter_jwtPrincipal_noPreferredUsername_usesSubject() throws Exception {
        // no preferred_username claim at all -> subject
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "only-sub");
        Jwt jwt = jwt(claims);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                jwt, "n/a", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("only-sub");
    }

    @Test
    void doFilter_unauthenticatedAuthentication_noUserId() throws Exception {
        // isAuthenticated() == false branch
        Authentication auth = mock(Authentication.class);
        org.mockito.Mockito.when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    // ---------------------------------------------------------------------
    // X-Forwarded-For client IP arm (RequestLogSupport delegate) — covers the
    // clientIp attribute population non-default branch.
    // ---------------------------------------------------------------------

    @Test
    void doFilter_usesForwardedForClientIp() throws Exception {
        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getClientIp()).isEqualTo("203.0.113.7");
        assertThat(request.getAttribute(RequestLogSupport.ATTR_CLIENT_IP)).isEqualTo("203.0.113.7");
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static Map<String, Object> claims(String subject, String preferredUsername) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("preferred_username", preferredUsername);
        return claims;
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                claims
        );
    }
}
