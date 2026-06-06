package com.finance.portal.assistant.presentation.controller;

import com.finance.portal.AbstractIntegrationTest;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.assistant.application.AssistantService;
import com.finance.portal.assistant.application.AssistantService.AssistantReply;
import com.finance.portal.assistant.application.AssistantService.Status;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AssistantController IT: /api/assistant/chat endpoint'i public ama IP/JWT bazlı rate-limit'li.
 * AssistantService MockBean'lendiği için dış LLM (Groq/Gemini) hiç aranmaz.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantControllerIT extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;

    @MockBean AssistantService assistantService;
    @MockBean KeycloakUserAdminPort keycloakUserAdminPort;
    @MockBean KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    @MockBean UserRegistrationPort userRegistrationPort;
    @MockBean UserAccountStatusPort userAccountStatusPort;

    private static final String CHAT_PAYLOAD =
            "{\"messages\":[{\"role\":\"user\",\"content\":\"merhaba\"}]}";

    @BeforeEach
    void setUp() {
        when(userAccountStatusPort.isAccountEnabled(any())).thenReturn(true);
    }

    // =========================================================================
    // Public / unauthenticated access
    // =========================================================================

    @Test
    void chat_isPublic_anonCallReachesService() throws Exception {
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.ok("Merhaba!"));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("OK"))
                .andExpect(jsonPath("$.data.reply").value("Merhaba!"));
    }

    @Test
    void chat_authenticatedJwt_userIdEmailAndNameForwarded() throws Exception {
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.ok("Selam Berkan"));

        mockMvc.perform(post("/api/v1/assistant/chat").with(jwt("user-42", "Berkan", "berkan@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(status().isOk());

        ArgumentCaptor<String> userIdCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userNameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userEmailCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ipCap = ArgumentCaptor.forClass(String.class);
        verify(assistantService).chat(any(), userIdCap.capture(),
                userNameCap.capture(), userEmailCap.capture(), ipCap.capture());

        assertThat(userIdCap.getValue()).isEqualTo("user-42");
        assertThat(userNameCap.getValue()).isEqualTo("Berkan");
        assertThat(userEmailCap.getValue()).isEqualTo("berkan@example.com");
    }

    @Test
    void chat_anonNoJwt_passesNullsForUserFields() throws Exception {
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.ok("anon yanıt"));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(status().isOk());

        ArgumentCaptor<String> userIdCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCap = ArgumentCaptor.forClass(String.class);
        verify(assistantService).chat(any(), userIdCap.capture(), any(), emailCap.capture(), any());

        assertThat(userIdCap.getValue()).isNull();
        assertThat(emailCap.getValue()).isNull();
    }

    // =========================================================================
    // X-Forwarded-For → clientIp extraction
    // =========================================================================

    @Test
    void chat_xForwardedFor_clientIpExtracted() throws Exception {
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.ok("ok"));

        // GCLB biçimi: "<client-ip>,<lb-ip>". Gerçek istemci IP'si SONDAN 2.'dir (trustedProxyCount=1).
        // 203.0.113.42 = gerçek istemci, 10.0.0.1 = yük dengeleyici → istemci IP'si seçilmeli.
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Forwarded-For", "203.0.113.42, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ipCap = ArgumentCaptor.forClass(String.class);
        verify(assistantService).chat(any(), any(), any(), any(), ipCap.capture());

        // GCLB'nin eklediği LB-IP (sonuncu) atlanır → gerçek istemci IP'si (sondan 2.).
        assertThat(ipCap.getValue()).isEqualTo("203.0.113.42");
    }

    @Test
    void chat_xForwardedFor_spoofedLeftmostIgnored_realClientUsed() throws Exception {
        // GÜVENLİK/REGRESYON: istemci-kontrollü leftmost girdi (spoof) DİKKATE ALINMAZ.
        // GCLB XFF'i korur+sona "<client>,<lb>" ekler → "spoof, gerçek-client, lb".
        // trustedProxyCount=1 → lb atlanır, sondan 2. (gerçek client) alınır; spoofa BAKILMAZ.
        // Bu sayede istemci her istekte farklı leftmost göndererek anonim sayacı sıfırlayamaz.
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.ok("ok"));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header("X-Forwarded-For", "6.6.6.6, 203.0.113.42, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(status().isOk());

        ArgumentCaptor<String> ipCap = ArgumentCaptor.forClass(String.class);
        verify(assistantService).chat(any(), any(), any(), any(), ipCap.capture());

        assertThat(ipCap.getValue()).isEqualTo("203.0.113.42"); // spoof (6.6.6.6) DEĞİL
    }

    // =========================================================================
    // Email verification (email_verified=false → null email forwarded)
    // =========================================================================

    @Test
    void chat_emailNotVerified_blockedAt403() throws Exception {
        // EmailVerifiedFilter doğrulanmamış e-postalı JWT'yi 403 ile bloke eder;
        // service'e hiç ulaşmaz. (Anon istek serbest kalırken, JWT taşıyıp
        // doğrulanmamış e-posta olan kullanıcı kasıtlı şekilde reddedilir.)
        mockMvc.perform(post("/api/v1/assistant/chat").with(jwtUnverifiedEmail("user-99"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Status pass-through (Status enum tüm değerleri)
    // =========================================================================

    @Test
    void chat_loginRequiredStatus_passedThrough() throws Exception {
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.of(Status.LOGIN_REQUIRED));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOGIN_REQUIRED"))
                .andExpect(jsonPath("$.data.reply").doesNotExist());
    }

    @Test
    void chat_rateLimited_passedThrough() throws Exception {
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.of(Status.RATE_LIMITED));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(jsonPath("$.data.status").value("RATE_LIMITED"));
    }

    @Test
    void chat_invalidStatus_passedThrough() throws Exception {
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.of(Status.INVALID));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(jsonPath("$.data.status").value("INVALID"));
    }

    @Test
    void chat_unavailable_passedThrough() throws Exception {
        when(assistantService.chat(any(), any(), any(), any(), any()))
                .thenReturn(AssistantReply.of(Status.UNAVAILABLE));

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAT_PAYLOAD))
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"));
    }

    // -----------------------------------------------------------------------

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String subject, String givenName, String email) {
        Jwt jwt = new Jwt(
                "mock-" + subject, Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", subject,
                        "given_name", givenName,
                        "email", email,
                        "email_verified", true,
                        "realm_access", Map.of("roles", List.of("USER"))
                )
        );
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtUnverifiedEmail(String subject) {
        Jwt jwt = new Jwt(
                "mock-" + subject, Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", subject,
                        "email", "unverified@example.com",
                        "email_verified", false,
                        "realm_access", Map.of("roles", List.of("USER"))
                )
        );
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
    }
}
