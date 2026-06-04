package com.finance.portal.common.presentation.filter;

import com.finance.portal.common.application.logging.RequestLogSupport;
import com.finance.portal.common.application.logging.model.RequestLogEvent;
import com.finance.portal.common.application.logging.port.RequestLogPublisherPort;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional branch-coverage tests for {@link RequestLoggingFilter} that complement
 * {@code RequestLoggingFilterTest}. These specifically exercise branches the original
 * suite misses:
 *
 * <ul>
 *   <li>Valid OpenTelemetry span present: capture trace/span id at request start
 *       ({@code spanCtx.isValid()} true arm), root-span enrichment block (with and
 *       without a userId), and the captured-id propagation into MDC + request
 *       attributes ({@code traceId/spanId} non-blank true arms).</li>
 *   <li>Valid span + chain throws: {@code recordException} / {@code setStatus} on the
 *       active span (exception-on-span block) plus rethrow.</li>
 *   <li>No span but {@code trace_id}/{@code span_id} already in {@link ThreadContext}:
 *       the MDC-fallback arms (lines that read {@code ThreadContext.get("trace_id")} /
 *       {@code "span_id"} when the captured values are null).</li>
 *   <li>{@code extractUserId} catch arm: principal access throws -> swallowed, null id.</li>
 * </ul>
 */
class RequestLoggingFilterMoreTest {

    // A syntactically valid, sampled W3C trace context (non-zero ids -> isValid()==true).
    private static final String VALID_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String VALID_SPAN_ID  = "b7ad6b7169203331";

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        ThreadContext.clearAll();
    }

    private static Scope makeCurrentValidSpan() {
        SpanContext ctx = SpanContext.create(
                VALID_TRACE_ID, VALID_SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
        return Span.wrap(ctx).makeCurrent();
    }

    // ------------------------------------------------------------------
    // Valid span, anonymous (no userId): captures trace/span ids, enriches
    // span without enduser.id, propagates ids into MDC + request attributes.
    // ------------------------------------------------------------------
    @Test
    void doFilter_validSpan_noUser_capturesAndPropagatesTraceIds() throws Exception {
        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/traced");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        try (Scope scope = makeCurrentValidSpan()) {
            filter.doFilter(request, response, new MockFilterChain());
        }

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        RequestLogEvent event = captor.getValue();
        // captured-at-start ids end up on the event and request attributes
        assertThat(event.getTraceId()).isEqualTo(VALID_TRACE_ID);
        assertThat(event.getSpanId()).isEqualTo(VALID_SPAN_ID);
        assertThat(event.getUserId()).isNull();
        assertThat(request.getAttribute(RequestLogSupport.ATTR_TRACE_ID)).isEqualTo(VALID_TRACE_ID);
        assertThat(request.getAttribute(RequestLogSupport.ATTR_SPAN_ID)).isEqualTo(VALID_SPAN_ID);
    }

    // ------------------------------------------------------------------
    // Valid span + authenticated user: covers the `userId != null` true arm
    // inside the span-enrichment block (span.setAttribute("enduser.id", ...)).
    // ------------------------------------------------------------------
    @Test
    void doFilter_validSpan_withUser_enrichesEnduserId() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "carol", "pw", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me/traced");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        try (Scope scope = makeCurrentValidSpan()) {
            filter.doFilter(request, response, new MockFilterChain());
        }

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        RequestLogEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo("carol");
        assertThat(event.getTraceId()).isEqualTo(VALID_TRACE_ID);
        assertThat(event.getSpanId()).isEqualTo(VALID_SPAN_ID);
    }

    // ------------------------------------------------------------------
    // Valid span + chain throws: exercises recordException/setStatus on the
    // active span (catch block span-handling) and still rethrows + publishes.
    // ------------------------------------------------------------------
    @Test
    void doFilter_validSpan_chainThrows_recordsOnSpanAndRethrows() throws Exception {
        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/traced/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200); // exception forces 500/ERROR

        RuntimeException boom = new IllegalArgumentException("traced-boom");
        FilterChain throwingChain = (req, res) -> { throw boom; };

        try (Scope scope = makeCurrentValidSpan()) {
            assertThatThrownBy(() -> filter.doFilter(request, response, throwingChain))
                    .isSameAs(boom);
        }

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        RequestLogEvent event = captor.getValue();
        assertThat(event.getLevel()).isEqualTo("ERROR");
        assertThat(event.getStatus()).isEqualTo("500");
        assertThat(event.getException()).contains("IllegalArgumentException").contains("traced-boom");
        // captured ids still propagate even on the error path
        assertThat(event.getTraceId()).isEqualTo(VALID_TRACE_ID);
        assertThat(event.getSpanId()).isEqualTo(VALID_SPAN_ID);
    }

    // ------------------------------------------------------------------
    // No span, but trace_id/span_id already in MDC (as a log4j/OTel context
    // provider would set): covers the `traceId == null -> ThreadContext.get`
    // fallback arms and the subsequent non-blank propagation.
    // ------------------------------------------------------------------
    @Test
    void doFilter_noSpan_usesThreadContextTraceIdFallback() throws Exception {
        ThreadContext.put("trace_id", "ffeeddccbbaa99887766554433221100");
        ThreadContext.put("span_id", "1122334455667788");

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mdc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // No current span made -> capturedTraceId stays null -> MDC fallback taken.
        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        RequestLogEvent event = captor.getValue();
        assertThat(event.getTraceId()).isEqualTo("ffeeddccbbaa99887766554433221100");
        assertThat(event.getSpanId()).isEqualTo("1122334455667788");
        assertThat(request.getAttribute(RequestLogSupport.ATTR_TRACE_ID))
                .isEqualTo("ffeeddccbbaa99887766554433221100");
        assertThat(request.getAttribute(RequestLogSupport.ATTR_SPAN_ID))
                .isEqualTo("1122334455667788");
    }

    // ------------------------------------------------------------------
    // No span, MDC has a blank trace_id/span_id: the fallback read happens and
    // assigns the (blank) values, but the `!isBlank()` guards stay false so the
    // ids are NOT pushed back into MDC and NO request attributes are set. This
    // hits the "first operand true, second false" side of `x != null && !blank`.
    // ------------------------------------------------------------------
    @Test
    void doFilter_noSpan_blankThreadContextTraceId_doesNotSetAttributes() throws Exception {
        ThreadContext.put("trace_id", "   ");
        ThreadContext.put("span_id", "");

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mdc-blank");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        RequestLogEvent event = captor.getValue();
        // The blank values are carried onto the event verbatim (not null)...
        assertThat(event.getTraceId()).isBlank();
        assertThat(event.getSpanId()).isBlank();
        // ...but the !isBlank() guards prevent setting request attributes.
        assertThat(request.getAttribute(RequestLogSupport.ATTR_TRACE_ID)).isNull();
        assertThat(request.getAttribute(RequestLogSupport.ATTR_SPAN_ID)).isNull();
    }

    // ------------------------------------------------------------------
    // extractUserId catch arm: principal access throws -> swallowed, no userId.
    // ------------------------------------------------------------------
    @Test
    void doFilter_extractUserIdThrows_isSwallowed_noUserId() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenThrow(new RuntimeException("principal blew up"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestLogPublisherPort publisher = mock(RequestLogPublisherPort.class);
        RequestLoggingFilter filter = new RequestLoggingFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/boom-principal");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<RequestLogEvent> captor = ArgumentCaptor.forClass(RequestLogEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    // ------------------------------------------------------------------
    // ERROR-with-no-exception path through logAtLevel using a null publisher,
    // so the publisher==null arm AND the ERROR(no-exception) log arm both run
    // together (existing 5xx test uses a present publisher).
    // ------------------------------------------------------------------
    @Test
    void doFilter_5xx_nullPublisher_errorLevelNoException() throws Exception {
        RequestLoggingFilter filter = new RequestLoggingFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/server-error");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // null publisher -> no NPE, chain still ran
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(RequestLogSupport.ATTR_REQUEST_ID)).isNotNull();
    }
}
