package com.finance.portal.alarm.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.AbstractIntegrationTest;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.alarm.repository.AlarmRepository;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlarmControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AlarmRepository alarmRepository;

    @MockBean
    KeycloakUserAdminPort keycloakUserAdminPort;
    @MockBean
    KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;
    @MockBean
    UserRegistrationPort userRegistrationPort;
    @MockBean
    UserAccountStatusPort userAccountStatusPort;

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
    void listAlarms_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/alarms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAlarm_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/alarms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // POST /api/alarms — create
    // =========================================================================

    @Test
    void createAlarm_happyPath_returns201() throws Exception {
        mockMvc.perform(post("/api/alarms")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetType": "STOCK",
                                  "symbol": "THYAO",
                                  "metric": "PRICE",
                                  "direction": "ABOVE",
                                  "threshold": 305.50,
                                  "frequency": "ONCE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("THYAO"))
                .andExpect(jsonPath("$.data.assetType").value("STOCK"))
                .andExpect(jsonPath("$.data.direction").value("ABOVE"))
                .andExpect(jsonPath("$.data.threshold").value(305.50))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void createAlarm_blankSymbol_returns400() throws Exception {
        mockMvc.perform(post("/api/alarms")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetType": "STOCK",
                                  "symbol": "",
                                  "metric": "PRICE",
                                  "direction": "ABOVE",
                                  "threshold": 100
                                }
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createAlarm_invalidAssetType_returns400() throws Exception {
        mockMvc.perform(post("/api/alarms")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetType": "GARBAGE",
                                  "symbol": "X",
                                  "metric": "PRICE",
                                  "direction": "ABOVE",
                                  "threshold": 100
                                }
                                """))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // GET /api/alarms — list
    // =========================================================================

    @Test
    void listAlarms_returnsOnlyOwn() throws Exception {
        // A bir alarm oluştursun
        mockMvc.perform(post("/api/alarms")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assetType":"STOCK","symbol":"THYAO","metric":"PRICE","direction":"ABOVE","threshold":300}
                        """))
                .andExpect(status().isCreated());

        // B'nin kendi listesi boş olmalı
        mockMvc.perform(get("/api/alarms").with(jwt(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        // A'nın listesinde 1 alarm görünmeli
        mockMvc.perform(get("/api/alarms").with(jwt(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].symbol").value("THYAO"));
    }

    // =========================================================================
    // GET /api/alarms/{id} — single
    // =========================================================================

    @Test
    void getAlarm_otherUsersAlarm_returns404() throws Exception {
        // A oluşturur
        String json = mockMvc.perform(post("/api/alarms")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assetType":"CRYPTO","symbol":"BTC","metric":"PRICE","direction":"BELOW","threshold":50000}
                        """))
                .andReturn().getResponse().getContentAsString();
        String alarmId = objectMapper.readTree(json).path("data").path("id").asText();

        // B okumaya çalışır → 404 (kullanıcıya göre kapsanır)
        mockMvc.perform(get("/api/alarms/" + alarmId).with(jwt(USER_B)))
                .andExpect(status().isNotFound());

        // A okuyabilir
        mockMvc.perform(get("/api/alarms/" + alarmId).with(jwt(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("BTC"));
    }

    // =========================================================================
    // PATCH /api/alarms/{id} — update
    // =========================================================================

    @Test
    void updateAlarm_changeThreshold_persists() throws Exception {
        String json = mockMvc.perform(post("/api/alarms")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assetType":"STOCK","symbol":"THYAO","metric":"PRICE","direction":"ABOVE","threshold":300}
                        """))
                .andReturn().getResponse().getContentAsString();
        String alarmId = objectMapper.readTree(json).path("data").path("id").asText();

        mockMvc.perform(patch("/api/alarms/" + alarmId)
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"threshold": 350.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threshold").value(350.0));

        // Verify persisted
        mockMvc.perform(get("/api/alarms/" + alarmId).with(jwt(USER_A)))
                .andExpect(jsonPath("$.data.threshold").value(350.0));
    }

    @Test
    void updateAlarm_otherUsers_returns404() throws Exception {
        String json = mockMvc.perform(post("/api/alarms")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assetType":"STOCK","symbol":"AKBNK","metric":"PRICE","direction":"ABOVE","threshold":50}
                        """))
                .andReturn().getResponse().getContentAsString();
        String alarmId = objectMapper.readTree(json).path("data").path("id").asText();

        mockMvc.perform(patch("/api/alarms/" + alarmId)
                        .with(jwt(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"threshold": 999}
                                """))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Alarm silme endpoint testleri (DELETE)
    // =========================================================================

    @Test
    void deleteAlarm_happyPath_returns2xx() throws Exception {
        String json = mockMvc.perform(post("/api/alarms")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assetType":"FX","symbol":"USD","metric":"PRICE","direction":"ABOVE","threshold":45}
                        """))
                .andReturn().getResponse().getContentAsString();
        String alarmId = objectMapper.readTree(json).path("data").path("id").asText();

        mockMvc.perform(delete("/api/alarms/" + alarmId).with(jwt(USER_A)))
                .andExpect(status().is2xxSuccessful());

        // List artık 0 olmalı
        mockMvc.perform(get("/api/alarms").with(jwt(USER_A)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void deleteAlarm_otherUsers_returns404() throws Exception {
        String json = mockMvc.perform(post("/api/alarms")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"assetType":"GOLD","symbol":"GRAM","metric":"PRICE","direction":"ABOVE","threshold":3000}
                        """))
                .andReturn().getResponse().getContentAsString();
        String alarmId = objectMapper.readTree(json).path("data").path("id").asText();

        mockMvc.perform(delete("/api/alarms/" + alarmId).with(jwt(USER_B)))
                .andExpect(status().isNotFound());

        // A'nın listesinde hala duruyor
        mockMvc.perform(get("/api/alarms").with(jwt(USER_A)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // =========================================================================
    // JWT helper
    // =========================================================================

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String subject) {
        Jwt jwt = new Jwt(
                "mock-token-" + subject,
                Instant.now(),
                Instant.now().plusSeconds(3600),
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
