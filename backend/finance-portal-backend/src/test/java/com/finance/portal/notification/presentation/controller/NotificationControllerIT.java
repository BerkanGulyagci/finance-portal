package com.finance.portal.notification.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.notification.application.port.EmailSenderPort;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.Notification;
import com.finance.portal.notification.domain.NotificationType;
import com.finance.portal.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Testcontainers
class NotificationControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("notif_it_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;

    @MockBean EmailSenderPort emailSenderPort;          // email gerçekten gönderilmesin
    @MockBean KeycloakUserAdminPort keycloakUserAdminPort;
    @MockBean KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    @MockBean UserRegistrationPort userRegistrationPort;
    @MockBean UserAccountStatusPort userAccountStatusPort;

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    @BeforeEach
    void setUp() {
        when(userAccountStatusPort.isAccountEnabled(any())).thenReturn(true);
    }

    // =========================================================================
    // Security
    // =========================================================================

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // GET /api/notifications — sadece kendi bildirimleri
    // =========================================================================

    @Test
    void list_returnsOnlyOwnNotifications() throws Exception {
        // A için 2, B için 1 bildirim
        notificationService.createAndSend(USER_A, NotificationType.ALARM, "A1", "body", null, null, null);
        notificationService.createAndSend(USER_A, NotificationType.ALARM, "A2", "body", null, null, null);
        notificationService.createAndSend(USER_B, NotificationType.ALARM, "B1", "body", null, null, null);

        mockMvc.perform(get("/api/notifications").with(jwt(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/notifications").with(jwt(USER_B)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // =========================================================================
    // GET /api/notifications/unread-count
    // =========================================================================

    @Test
    void unreadCount_returnsNumberOfUnread() throws Exception {
        notificationService.createAndSend(USER_A, NotificationType.ALARM, "T1", "b", null, null, null);
        notificationService.createAndSend(USER_A, NotificationType.ALARM, "T2", "b", null, null, null);
        notificationService.createAndSend(USER_A, NotificationType.ALARM, "T3", "b", null, null, null);

        mockMvc.perform(get("/api/notifications/unread-count").with(jwt(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void unreadCount_initiallyZero() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count").with(jwt(USER_A)))
                .andExpect(jsonPath("$.data").value(0));
    }

    // =========================================================================
    // POST /api/notifications/{id}/read — tek tek
    // =========================================================================

    @Test
    void markRead_marksSingleNotification() throws Exception {
        Notification n = notificationService.createAndSend(
                USER_A, NotificationType.ALARM, "T", "b", null, null, null);

        mockMvc.perform(post("/api/notifications/" + n.getId() + "/read").with(jwt(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        // Unread count düştü
        mockMvc.perform(get("/api/notifications/unread-count").with(jwt(USER_A)))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    void markRead_otherUsersNotification_returns404() throws Exception {
        Notification n = notificationService.createAndSend(
                USER_A, NotificationType.ALARM, "T", "b", null, null, null);

        mockMvc.perform(post("/api/notifications/" + n.getId() + "/read").with(jwt(USER_B)))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRead_unknownId_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(post("/api/notifications/" + unknown + "/read").with(jwt(USER_A)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // POST /api/notifications/read-all
    // =========================================================================

    @Test
    void markAllRead_marksOnlyOwn() throws Exception {
        notificationService.createAndSend(USER_A, NotificationType.ALARM, "A1", "b", null, null, null);
        notificationService.createAndSend(USER_A, NotificationType.ALARM, "A2", "b", null, null, null);
        notificationService.createAndSend(USER_B, NotificationType.ALARM, "B1", "b", null, null, null);

        mockMvc.perform(post("/api/notifications/read-all").with(jwt(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));   // affected rows = 2

        // B'nin bildirimi okunmamış kaldı
        mockMvc.perform(get("/api/notifications/unread-count").with(jwt(USER_B)))
                .andExpect(jsonPath("$.data").value(1));
    }

    // =========================================================================
    // DELETE /api/notifications/{id}
    // =========================================================================

    @Test
    void delete_removesOwnNotification() throws Exception {
        Notification n = notificationService.createAndSend(
                USER_A, NotificationType.ALARM, "T", "b", null, null, null);

        mockMvc.perform(delete("/api/notifications/" + n.getId()).with(jwt(USER_A)))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/notifications").with(jwt(USER_A)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void delete_otherUsersNotification_returns404() throws Exception {
        Notification n = notificationService.createAndSend(
                USER_A, NotificationType.ALARM, "T", "b", null, null, null);

        mockMvc.perform(delete("/api/notifications/" + n.getId()).with(jwt(USER_B)))
                .andExpect(status().isNotFound());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String subject) {
        Jwt jwt = new Jwt(
                "mock-" + subject, Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", subject,
                        "email", subject + "@example.com",
                        "email_verified", true,
                        "realm_access", Map.of("roles", List.of("USER"))
                )
        );
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
    }
}
