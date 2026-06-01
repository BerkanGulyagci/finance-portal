package com.finance.portal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.portfolio.repository.PortfolioRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortfolioIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PortfolioRepository portfolioRepository;

    @MockBean
    AssetPriceQueryService assetPriceQueryService;

    // Keycloak admin/registration port'ları — gerçek KC olmadığı için mock'lanır.
    // PortfolioIT'de JWT zaten mock olarak enjekte edildiği için bu port'lara hiç çağrı
    // yapılmamalı; emniyet için mock'larlar.
    @MockBean
    KeycloakUserAdminPort keycloakUserAdminPort;

    @MockBean
    KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;

    @MockBean
    UserRegistrationPort userRegistrationPort;

    // DisabledAccountFilter her request'te bunu çağırıyor → Keycloak admin API'sini tetikliyor.
    // Test'lerde "tüm user'lar enabled" varsayalım.
    @MockBean
    UserAccountStatusPort userAccountStatusPort;

    private static final String USER_A = "user-a-subject";
    private static final String USER_B = "user-b-subject";

    @BeforeEach
    void setUp() {
        // AssetPriceQueryService mock — tüm çağrılar için sabit snapshot döner
        AssetPriceSnapshot mockSnapshot = new AssetPriceSnapshot(
                AssetType.STOCK,
                "THYAO.IS",
                new BigDecimal("100.00"),
                "TRY",
                LocalDateTime.now()
        );
        when(assetPriceQueryService.getCurrentPrice(any(), any())).thenReturn(mockSnapshot);
        // DisabledAccountFilter her request'te bu portu çağırıyor; test'te herkes aktif.
        when(userAccountStatusPort.isAccountEnabled(any())).thenReturn(true);
    }

    // =========================================================================
    // Security — token olmadan 401
    // =========================================================================

    @Test
    void getPortfolios_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/portfolios").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPortfolio_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "My Portfolio"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addTransaction_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/portfolios/00000000-0000-0000-0000-000000000001/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Test
    void createPortfolio_withValidJwt_returns201() throws Exception {
        mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "My Stock Portfolio", "currency": "USD"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("My Stock Portfolio"))
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void getUserPortfolios_withValidJwt_returns200() throws Exception {
        // önce bir portfolio oluştur
        mockMvc.perform(post("/api/portfolios")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Portfolio For List Test"}
                        """));

        mockMvc.perform(get("/api/portfolios")
                        .with(jwt(USER_A))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void addBuyTransaction_withValidJwt_returns200() throws Exception {
        // portfolio oluştur
        String createResponse = mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Tx Test Portfolio"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(createResponse)
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "THYAO.IS",
                                  "assetType": "STOCK",
                                  "transactionType": "BUY",
                                  "quantity": 10,
                                  "price": 150.00,
                                  "transactionDate": "2026-01-15T10:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactions[0].symbol").value("THYAO.IS"));
    }

    // =========================================================================
    // Validation / business rules
    // =========================================================================

    @Test
    void createPortfolio_duplicateName_returnsBadRequest() throws Exception {
        String body = """
                {"name": "Duplicate Portfolio"}
                """;

        mockMvc.perform(post("/api/portfolios")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // aynı isimle ikinci kez
        mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void addTransaction_sellMoreThanAvailable_returnsBadRequest() throws Exception {
        // portfolio oluştur
        String createResponse = mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sell Validation Portfolio"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(createResponse)
                .path("data").path("id").asText();

        // 5 adet BUY
        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                .with(jwt(USER_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "symbol": "THYAO.IS",
                          "assetType": "STOCK",
                          "transactionType": "BUY",
                          "quantity": 5,
                          "price": 100.00,
                          "transactionDate": "2026-01-15T10:00:00"
                        }
                        """));

        // 10 adet SELL — yetersiz quantity
        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "THYAO.IS",
                                  "assetType": "STOCK",
                                  "transactionType": "SELL",
                                  "quantity": 10,
                                  "price": 120.00,
                                  "transactionDate": "2026-01-16T10:00:00"
                                }
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void addTransaction_invalidBody_blankSymbol_returns400() throws Exception {
        String createResponse = mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Validation Test Portfolio"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(createResponse)
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "",
                                  "assetType": "STOCK",
                                  "transactionType": "BUY",
                                  "quantity": 10,
                                  "price": 100.00,
                                  "transactionDate": "2026-01-15T10:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addTransaction_invalidBody_negativeQuantity_returns400() throws Exception {
        String createResponse = mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Negative Qty Portfolio"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(createResponse)
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "THYAO.IS",
                                  "assetType": "STOCK",
                                  "transactionType": "BUY",
                                  "quantity": -5,
                                  "price": 100.00,
                                  "transactionDate": "2026-01-15T10:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Ownership isolation
    // =========================================================================

    @Test
    void getPortfolioById_otherUsersPortfolio_returns4xx() throws Exception {
        // user A portfolio oluşturur
        String createResponse = mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "User A Private Portfolio"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(createResponse)
                .path("data").path("id").asText();

        // user B aynı portfolio'ya erişmeye çalışır
        mockMvc.perform(get("/api/portfolios/" + portfolioId)
                        .with(jwt(USER_B))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void addTransaction_toOtherUsersPortfolio_returns4xx() throws Exception {
        // user A portfolio oluşturur
        String createResponse = mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "User A Tx Isolation Portfolio"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(createResponse)
                .path("data").path("id").asText();

        // user B transaction eklemeye çalışır
        mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .with(jwt(USER_B))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "THYAO.IS",
                                  "assetType": "STOCK",
                                  "transactionType": "BUY",
                                  "quantity": 10,
                                  "price": 100.00,
                                  "transactionDate": "2026-01-15T10:00:00"
                                }
                                """))
                .andExpect(status().is4xxClientError());
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
                        // EmailVerifiedFilter /api/portfolios üzerinde email_verified=true talep ediyor.
                        "email_verified", true,
                        "email", subject + "@example.com",
                        "realm_access", Map.of("roles", List.of("USER"))
                )
        );
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
    }
}
