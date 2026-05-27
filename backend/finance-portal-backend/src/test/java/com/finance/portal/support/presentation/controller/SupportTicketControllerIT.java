package com.finance.portal.support.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.notification.application.port.EmailSenderPort;
import com.finance.portal.support.application.service.SupportTicketService;
import com.finance.portal.support.domain.SupportTicket;
import com.finance.portal.support.domain.SupportTicketStatus;
import com.finance.portal.support.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers
class SupportTicketControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("support_it_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SupportTicketService supportTicketService;
    @Autowired SupportTicketRepository repository;

    @MockBean EmailSenderPort emailSenderPort;
    @MockBean KeycloakUserAdminPort keycloakUserAdminPort;
    @MockBean KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    @MockBean UserRegistrationPort userRegistrationPort;
    @MockBean UserAccountStatusPort userAccountStatusPort;

    private static final String USER_A = "support-user-a";
    private static final String USER_B = "support-user-b";

    @BeforeEach
    void setUp() {
        when(userAccountStatusPort.isAccountEnabled(any())).thenReturn(true);
        when(keycloakUserAdminPort.findUsersByRealmRole(any())).thenReturn(List.of());
    }

    // =========================================================================
    // Security
    // =========================================================================

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/support/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/support/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"t\",\"message\":\"m\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminListUserTickets_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + USER_A + "/tickets").with(jwt(USER_B, "USER")))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // POST /api/support/tickets — create
    // =========================================================================

    @Test
    void create_validRequest_returnsTicket() throws Exception {
        mockMvc.perform(post("/api/support/tickets").with(jwt(USER_A, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Login problemi\",\"message\":\"Giriş yapamıyorum.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subject").value("Login problemi"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        assertThat(repository.findByUserIdOrderByCreatedAtDesc(USER_A)).hasSize(1);
    }

    @Test
    void create_blankSubject_returns400() throws Exception {
        mockMvc.perform(post("/api/support/tickets").with(jwt(USER_A, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"\",\"message\":\"non-empty\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_activeTicketLimit_returns400() throws Exception {
        // 3 açık talep (MAX_ACTIVE_TICKETS).
        for (int i = 0; i < 3; i++) {
            supportTicketService.create(USER_A, "a@example.com", "user-a",
                    "Subj " + i, "Msg " + i);
        }
        // 4. denemede 400.
        mockMvc.perform(post("/api/support/tickets").with(jwt(USER_A, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"another\",\"message\":\"m\"}"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GET /api/support/tickets — own tickets only
    // =========================================================================

    @Test
    void list_returnsOnlyOwnTickets() throws Exception {
        supportTicketService.create(USER_A, "a@example.com", "user-a", "A-1", "m");
        supportTicketService.create(USER_A, "a@example.com", "user-a", "A-2", "m");
        supportTicketService.create(USER_B, "b@example.com", "user-b", "B-1", "m");

        mockMvc.perform(get("/api/support/tickets").with(jwt(USER_A, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/support/tickets").with(jwt(USER_B, "USER")))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // =========================================================================
    // PUT /api/support/tickets/{id} — update
    // =========================================================================

    @Test
    void update_ownOpenTicket_succeeds() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "old", "old-msg");

        mockMvc.perform(put("/api/support/tickets/" + t.getId()).with(jwt(USER_A, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"yeni konu\",\"message\":\"yeni mesaj\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("yeni konu"))
                .andExpect(jsonPath("$.data.message").value("yeni mesaj"));
    }

    @Test
    void update_otherUsersTicket_returns404() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "A", "m");

        mockMvc.perform(put("/api/support/tickets/" + t.getId()).with(jwt(USER_B, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"hack\",\"message\":\"m\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_inProgressTicket_returns400() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "A", "m");
        supportTicketService.updateStatus(t.getId(), SupportTicketStatus.IN_PROGRESS, null);

        mockMvc.perform(put("/api/support/tickets/" + t.getId()).with(jwt(USER_A, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"new\",\"message\":\"new\"}"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Ticket silme endpoint testleri (DELETE)
    // =========================================================================

    @Test
    void delete_ownOpenTicket_succeeds() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "to-delete", "m");

        mockMvc.perform(delete("/api/support/tickets/" + t.getId()).with(jwt(USER_A, "USER")))
                .andExpect(status().isOk());

        assertThat(repository.findById(t.getId())).isEmpty();
    }

    @Test
    void delete_otherUsersTicket_returns404() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "A", "m");

        mockMvc.perform(delete("/api/support/tickets/" + t.getId()).with(jwt(USER_B, "USER")))
                .andExpect(status().isNotFound());

        assertThat(repository.findById(t.getId())).isPresent();
    }

    @Test
    void delete_inProgressTicket_returns400() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "A", "m");
        supportTicketService.updateStatus(t.getId(), SupportTicketStatus.IN_PROGRESS, null);

        mockMvc.perform(delete("/api/support/tickets/" + t.getId()).with(jwt(USER_A, "USER")))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Admin endpoints — /api/admin/users/{userId}/tickets + PATCH .../status
    // =========================================================================

    @Test
    void adminListUserTickets_asAdmin_returnsTargetUsersTickets() throws Exception {
        supportTicketService.create(USER_A, "a@example.com", "user-a", "A-1", "m");
        supportTicketService.create(USER_A, "a@example.com", "user-a", "A-2", "m");

        mockMvc.perform(get("/api/admin/users/" + USER_A + "/tickets").with(jwt("admin-1", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void adminUpdateStatus_asAdmin_changesStatusAndNotifiesUser() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "S", "m");

        mockMvc.perform(patch("/api/admin/support/tickets/" + t.getId() + "/status")
                        .with(jwt("admin-1", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"adminNote\":\"ekibe atadık\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.adminNote").value("ekibe atadık"));

        assertThat(repository.findById(t.getId()).orElseThrow().getStatus())
                .isEqualTo(SupportTicketStatus.IN_PROGRESS);
    }

    @Test
    void adminUpdateStatus_asUser_returns403() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "S", "m");

        mockMvc.perform(patch("/api/admin/support/tickets/" + t.getId() + "/status")
                        .with(jwt(USER_B, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdateStatus_invalidStatus_returns400() throws Exception {
        SupportTicket t = supportTicketService.create(USER_A, "a@example.com", "user-a", "S", "m");

        mockMvc.perform(patch("/api/admin/support/tickets/" + t.getId() + "/status")
                        .with(jwt("admin-1", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOGUS\",\"adminNote\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminUpdateStatus_unknownTicket_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(patch("/api/admin/support/tickets/" + unknown + "/status")
                        .with(jwt("admin-1", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"adminNote\":\"\"}"))
                .andExpect(status().isNotFound());
    }

    // -----------------------------------------------------------------------

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String subject, String... roles) {
        Jwt jwt = new Jwt(
                "mock-" + subject, Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", subject,
                        "email", subject + "@example.com",
                        "email_verified", true,
                        "realm_access", Map.of("roles", List.of(roles))
                )
        );
        SimpleGrantedAuthority[] authorities = Arrays.stream(roles)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt).authorities(authorities);
    }
}
