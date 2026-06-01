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
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIT extends AbstractIntegrationTest {

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

    @Test
    void marketEndpointsShouldBePublic_noAuthRequired() throws Exception {
        // Public route: auth filter chain 401/403 vermeyecek. Backend'in
        // gerçek external HTTP çağrısı (Yahoo/Midas) yapması CI ortamında 500
        // verebilir — bu güvenlik test'i değil, network test'i. Sadece auth
        // katmanının izin verdiğini (yani 401/403 OLMADIĞINI) doğruluyoruz.
        int status = mockMvc.perform(get("/api/market/stocks/THYAO.IS").accept(APPLICATION_JSON))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status)
                .as("public endpoint security check (any non-auth status is OK)")
                .isNotEqualTo(401)
                .isNotEqualTo(403);
    }

    @Test
    void newsEndpointsShouldBePublic_noAuthRequired() throws Exception {
        int status = mockMvc.perform(get("/api/news").accept(APPLICATION_JSON))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status)
                .isNotEqualTo(401)
                .isNotEqualTo(403);
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
