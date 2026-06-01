package com.finance.portal.newsletter.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.AbstractIntegrationTest;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.newsletter.domain.NewsletterFrequency;
import com.finance.portal.newsletter.domain.NewsletterSubscription;
import com.finance.portal.newsletter.presentation.dto.UpdateNewsletterRequest;
import com.finance.portal.newsletter.repository.NewsletterSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NewsletterControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired NewsletterSubscriptionRepository repository;

    @MockBean KeycloakUserAdminPort keycloakUserAdminPort;
    @MockBean KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    @MockBean UserRegistrationPort userRegistrationPort;
    @MockBean UserAccountStatusPort userAccountStatusPort;

    private static final String USER_A = "newsletter-user-a";
    private static final String USER_B = "newsletter-user-b";

    @BeforeEach
    void setUp() {
        when(userAccountStatusPort.isAccountEnabled(any())).thenReturn(true);
    }

    // =========================================================================
    // Security
    // =========================================================================

    @Test
    void getMine_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/newsletter/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_withoutToken_returns401() throws Exception {
        UpdateNewsletterRequest req = new UpdateNewsletterRequest();
        req.setFrequency(NewsletterFrequency.DAILY);

        mockMvc.perform(put("/api/newsletter/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /api/newsletter/me
    // =========================================================================

    @Test
    void getMine_noExistingSubscription_returnsDefaults() throws Exception {
        mockMvc.perform(get("/api/newsletter/me").with(jwt(USER_A, "a@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subscribed").value(false))
                .andExpect(jsonPath("$.data.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.email").value("a@example.com"));
    }

    @Test
    void getMine_withExistingSubscription_returnsIt() throws Exception {
        // Önce abone et.
        UpdateNewsletterRequest req = new UpdateNewsletterRequest();
        req.setFrequency(NewsletterFrequency.MONTHLY);
        req.setEnabled(true);
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Sonra oku.
        mockMvc.perform(get("/api/newsletter/me").with(jwt(USER_A, "a@example.com")))
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.data.email").value("a@example.com"));
    }

    // =========================================================================
    // PUT /api/newsletter/me  (upsert)
    // =========================================================================

    @Test
    void update_createsNewSubscription() throws Exception {
        UpdateNewsletterRequest req = new UpdateNewsletterRequest();
        req.setFrequency(NewsletterFrequency.DAILY);
        req.setEnabled(true);

        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.frequency").value("DAILY"));

        NewsletterSubscription saved = repository.findByUserId(USER_A).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.isEnabled()).isTrue();
        org.assertj.core.api.Assertions.assertThat(saved.getFrequency()).isEqualTo(NewsletterFrequency.DAILY);
        org.assertj.core.api.Assertions.assertThat(saved.getEmail()).isEqualTo("a@example.com");
        org.assertj.core.api.Assertions.assertThat(saved.getUnsubscribeToken()).isNotBlank();
    }

    @Test
    void update_updatesExistingSubscriptionFrequency() throws Exception {
        // İlk olarak DAILY.
        UpdateNewsletterRequest first = new UpdateNewsletterRequest();
        first.setFrequency(NewsletterFrequency.DAILY);
        first.setEnabled(true);
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk());

        // Sonra WEEKLY'e güncelle.
        UpdateNewsletterRequest second = new UpdateNewsletterRequest();
        second.setFrequency(NewsletterFrequency.WEEKLY);
        second.setEnabled(true);
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(jsonPath("$.data.frequency").value("WEEKLY"));

        // DB'de tek kayıt, frekans güncellendi.
        org.assertj.core.api.Assertions.assertThat(repository.findByUserId(USER_A).orElseThrow().getFrequency())
                .isEqualTo(NewsletterFrequency.WEEKLY);
    }

    @Test
    void update_disablesSubscription() throws Exception {
        // Önce aboneliği aç.
        UpdateNewsletterRequest enable = new UpdateNewsletterRequest();
        enable.setFrequency(NewsletterFrequency.WEEKLY);
        enable.setEnabled(true);
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enable)))
                .andExpect(status().isOk());

        // Sonra kapat.
        UpdateNewsletterRequest disable = new UpdateNewsletterRequest();
        disable.setFrequency(NewsletterFrequency.WEEKLY);
        disable.setEnabled(false);
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disable)))
                .andExpect(jsonPath("$.data.subscribed").value(false));
    }

    @Test
    void update_missingFrequency_returns400() throws Exception {
        // @NotNull frequency yok — Bean Validation 400 vermeli.
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_perUserIsolation_doesNotLeakAcrossUsers() throws Exception {
        UpdateNewsletterRequest aReq = new UpdateNewsletterRequest();
        aReq.setFrequency(NewsletterFrequency.DAILY);
        aReq.setEnabled(true);
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aReq)))
                .andExpect(status().isOk());

        UpdateNewsletterRequest bReq = new UpdateNewsletterRequest();
        bReq.setFrequency(NewsletterFrequency.MONTHLY);
        bReq.setEnabled(true);
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_B, "b@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isOk());

        // A hâlâ DAILY, B MONTHLY.
        mockMvc.perform(get("/api/newsletter/me").with(jwt(USER_A, "a@example.com")))
                .andExpect(jsonPath("$.data.frequency").value("DAILY"));
        mockMvc.perform(get("/api/newsletter/me").with(jwt(USER_B, "b@example.com")))
                .andExpect(jsonPath("$.data.frequency").value("MONTHLY"));
    }

    // =========================================================================
    // GET /api/newsletter/unsubscribe?token=...  (public, no auth)
    // =========================================================================

    @Test
    void unsubscribe_withValidToken_disablesSubscriptionAndReturnsHtml() throws Exception {
        // Abone ol → token al → unsubscribe et.
        UpdateNewsletterRequest req = new UpdateNewsletterRequest();
        req.setFrequency(NewsletterFrequency.WEEKLY);
        req.setEnabled(true);
        mockMvc.perform(put("/api/newsletter/me")
                        .with(jwt(USER_A, "a@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        String token = repository.findByUserId(USER_A).orElseThrow().getUnsubscribeToken();

        mockMvc.perform(get("/api/newsletter/unsubscribe").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("iptal edildi")));

        org.assertj.core.api.Assertions.assertThat(repository.findByUserId(USER_A).orElseThrow().isEnabled())
                .isFalse();
    }

    @Test
    void unsubscribe_withInvalidToken_returnsHtmlWithFailureMessage() throws Exception {
        mockMvc.perform(get("/api/newsletter/unsubscribe").param("token", "nonexistent-token-xyz"))
                .andExpect(status().isOk())  // Public endpoint — sessiz 200, ama HTML hata mesajı içerir.
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("geçersiz")));
    }

    @Test
    void unsubscribe_isPublic_noAuthRequired() throws Exception {
        // Auth filter chain'in 401/403 vermediğini doğrula (token geçersiz olsa bile 200).
        int statusCode = mockMvc.perform(get("/api/newsletter/unsubscribe").param("token", "x"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(statusCode)
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    // -----------------------------------------------------------------------

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String subject, String email) {
        Jwt jwt = new Jwt(
                "mock-" + subject, Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", subject,
                        "email", email,
                        "email_verified", true,
                        "realm_access", Map.of("roles", List.of("USER"))
                )
        );
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
    }
}
