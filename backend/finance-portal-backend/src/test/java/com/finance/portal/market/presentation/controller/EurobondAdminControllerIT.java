package com.finance.portal.market.presentation.controller;

import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.market.application.bond.eurobond.port.HmbIsinSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EurobondAdminController için HTTP-katmanı IT. /api/admin/** ROLE_ADMIN korumalı;
 * xlsx refresh path için HmbIsinSource mock'lanır (network'e çıkılmaz).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EurobondAdminControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("eb_admin_it_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;

    @MockBean HmbIsinSource hmbIsinSource;
    @MockBean KeycloakUserAdminPort keycloakUserAdminPort;
    @MockBean KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    @MockBean UserRegistrationPort userRegistrationPort;
    @MockBean UserAccountStatusPort userAccountStatusPort;

    private static final String VALID_URL =
            "https://ms.hmb.gov.tr/uploads/2026/05/Merkezi_Yonetim_Dis_Borc_Stoku_Tahvil_Listesi-abc.xlsx";

    @BeforeEach
    void setUp() {
        when(userAccountStatusPort.isAccountEnabled(any())).thenReturn(true);
    }

    // =========================================================================
    // Security
    // =========================================================================

    @Test
    void getIsins_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/eurobonds/isins").accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/admin/eurobonds/refresh").param("xlsxUrl", VALID_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getIsins_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/eurobonds/isins").with(jwt("USER")).accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void refresh_asUser_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/eurobonds/refresh")
                        .with(jwt("USER"))
                        .param("xlsxUrl", VALID_URL))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /api/admin/eurobonds/isins
    // =========================================================================

    @Test
    void getIsins_asAdmin_returnsCurrentList() throws Exception {
        when(hmbIsinSource.isins()).thenReturn(List.of("US900123AL40", "XS1234567890"));

        mockMvc.perform(get("/api/admin/eurobonds/isins").with(jwt("ADMIN")).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.isins[0]").value("US900123AL40"));
    }

    // =========================================================================
    // POST /api/admin/eurobonds/refresh
    // =========================================================================

    @Test
    void refresh_asAdmin_validUrl_returnsCount() throws Exception {
        when(hmbIsinSource.refreshFromXlsx(eq(VALID_URL), eq(false))).thenReturn(42);

        mockMvc.perform(post("/api/admin/eurobonds/refresh")
                        .with(jwt("ADMIN"))
                        .param("xlsxUrl", VALID_URL)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(42))
                .andExpect(jsonPath("$.data.xlsxUrl").value(VALID_URL))
                .andExpect(jsonPath("$.data.force").value(false));
    }

    @Test
    void refresh_asAdmin_invalidUrl_returns400() throws Exception {
        // HmbIsinSource validasyonu IllegalArgumentException fırlatırsa controller 400 verir.
        when(hmbIsinSource.refreshFromXlsx(any(String.class), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Geçerli bir .xlsx URL'i verin."));

        mockMvc.perform(post("/api/admin/eurobonds/refresh")
                        .with(jwt("ADMIN"))
                        .param("xlsxUrl", "not-an-xlsx")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void refresh_asAdmin_wrongFilename_returns400WithKeywordMessage() throws Exception {
        // Provider'ın gerçek IllegalArgumentException davranışını taklit et (Yurt_Disi_Tahvil_Ihraclari reddi).
        String wrongUrl = "https://ms.hmb.gov.tr/uploads/2026/05/Merkezi_Yonetim_Yurt_Disi_Tahvil_Ihraclari-x.xlsx";
        when(hmbIsinSource.refreshFromXlsx(eq(wrongUrl), eq(false)))
                .thenThrow(new IllegalArgumentException(
                        "Bu URL HMB \"Dış Borç Stoku Tahvil Listesi\" gibi görünmüyor."));

        mockMvc.perform(post("/api/admin/eurobonds/refresh")
                        .with(jwt("ADMIN"))
                        .param("xlsxUrl", wrongUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Dış Borç Stoku")));
    }

    @Test
    void refresh_asAdmin_withForceTrue_forwardsFlag() throws Exception {
        String nonStandardUrl =
                "https://ms.hmb.gov.tr/uploads/2027/01/HMB_New_Scheme-x.xlsx";
        when(hmbIsinSource.refreshFromXlsx(eq(nonStandardUrl), eq(true))).thenReturn(25);

        mockMvc.perform(post("/api/admin/eurobonds/refresh")
                        .with(jwt("ADMIN"))
                        .param("xlsxUrl", nonStandardUrl)
                        .param("force", "true")
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(25))
                .andExpect(jsonPath("$.data.force").value(true));
    }

    @Test
    void refresh_asAdmin_providerDownloadFailure_returns502() throws Exception {
        when(hmbIsinSource.refreshFromXlsx(any(String.class), anyBoolean()))
                .thenThrow(new IllegalStateException("xlsx indirilemedi"));

        mockMvc.perform(post("/api/admin/eurobonds/refresh")
                        .with(jwt("ADMIN"))
                        .param("xlsxUrl", VALID_URL))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false));
    }

    // -----------------------------------------------------------------------

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String... roles) {
        Jwt jwt = new Jwt(
                "mock-eb-admin-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "eb-admin-user-id",
                        "preferred_username", "eb-admin",
                        "email_verified", true,
                        "email", "eb-admin@example.com",
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
