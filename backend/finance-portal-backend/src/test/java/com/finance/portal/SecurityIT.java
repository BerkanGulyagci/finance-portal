package com.finance.portal;

import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("security_it_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    // KC admin port'ları + account status — DisabledAccountFilter Keycloak'a gitmesin diye.
    @MockBean KeycloakUserAdminPort keycloakUserAdminPort;
    @MockBean KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    @MockBean UserRegistrationPort userRegistrationPort;
    @MockBean UserAccountStatusPort userAccountStatusPort;

    @BeforeEach
    void setUp() {
        when(userAccountStatusPort.isAccountEnabled(any())).thenReturn(true);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithRoles(String... roles) {
        Jwt jwt = new Jwt(
                "mock-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "test-user",
                        "preferred_username", "test-user",
                        "email_verified", true,
                        "email", "test-user@example.com",
                        "realm_access", Map.of("roles", List.of(roles))
                )
        );
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
    }

    @Test
    void marketEndpointsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/market/stocks/THYAO.IS").accept(APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void newsEndpointsShouldBePublic() throws Exception {
        mockMvc.perform(get("/api/news").accept(APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void kafkaTestShouldReturn401WithoutToken() throws Exception {
        // /api/kafka/test SecurityConfig'te authenticated() — endpoint silinmiş olsa bile
        // security filter chain'i 401 vermeli (authentication kontrolü erken yapılır).
        mockMvc.perform(post("/api/kafka/test").accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void portfoliosEndpointShouldReturn401WithoutToken() throws Exception {
        // Protected endpoint - auth olmadan erişilemez.
        mockMvc.perform(get("/api/portfolios").accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointShouldReturn401WithoutToken() throws Exception {
        // Admin endpoint - auth olmadan 401 (yetki rolden önce token gerekli).
        mockMvc.perform(get("/api/admin/users").accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
