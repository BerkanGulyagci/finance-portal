package com.finance.portal.admin;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.alarm.application.service.AlarmService;
import com.finance.portal.newsletter.application.service.NewsletterService;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityIntegrationTests {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    KeycloakUserAdminPort keycloakUserAdminPort;

    @MockBean
    UserBanStatePort userBanStatePort;

    @MockBean
    UserAccountStatusPort userAccountStatusPort;

    @MockBean
    AlarmService alarmService;

    @MockBean
    NewsletterService newsletterService;

    @MockBean
    NotificationService notificationService;

    @BeforeEach
    void setUpAccountStatus() {
        when(userAccountStatusPort.isAccountEnabled(anyString())).thenReturn(true);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithRoles(String... roles) {
        Jwt jwt = new Jwt(
                "mock-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "admin-user-id",
                        "preferred_username", "berkanadmin",
                        "realm_access", Map.of("roles", List.of(roles))
                )
        );
        SimpleGrantedAuthority[] authorities = java.util.Arrays.stream(roles)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toArray(SimpleGrantedAuthority[]::new);
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt).authorities(authorities);
    }

    @Test
    void adminEndpointsShouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/users").accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsShouldReturn403ForNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(jwtWithRoles("USER")).accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointsShouldReturn200ForAdminRole() throws Exception {
        when(keycloakUserAdminPort.listUsers(isNull(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(
                        new AdminUserView(
                                "u1", "test", "t@t.com", "T", "U", true, true, List.of("USER"),
                                null, false, BanStatus.ACTIVE, null
                        )
                ));
        when(userBanStatePort.findByUserIds(any())).thenReturn(Map.of());

        mockMvc.perform(get("/api/admin/users").with(jwtWithRoles("ADMIN")).accept(APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void banEndpointShouldReturn200ForAdminRole() throws Exception {
        when(keycloakUserAdminPort.getUser(anyString())).thenReturn(
                new AdminUserView(
                        "target", "user", "u@u.com", "U", "S", true, true, List.of("USER"),
                        null, false, BanStatus.ACTIVE, null
                )
        );

        mockMvc.perform(post("/api/admin/users/target/ban")
                        .with(jwtWithRoles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"banType\":\"PERMANENT\"}")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
