package com.finance.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.auth.application.port.KeycloakRegistrationFollowUpPort;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end portfolio transaction flow integration test.
 *
 * <p>Unlike a mocked unit test, this drives the FULL stack against a REAL PostgreSQL
 * container (Testcontainers): HTTP (MockMvc) → security/JWT → controller →
 * {@code PortfolioService} → JPA repositories → Flyway-migrated Postgres. Only the
 * outbound market-data port ({@link AssetPriceQueryService}) and Keycloak ports are
 * mocked, because they reach external systems; everything else is the production wiring.
 *
 * <p>Setup (container, dynamic datasource props, mocked Keycloak/account-status ports,
 * JWT post-processor) mirrors {@link PortfolioIT} exactly — that is the proven config.
 *
 * <p>Covered flow:
 * <ol>
 *   <li>Create a portfolio.</li>
 *   <li>Add STOCK BUY transactions and assert computed holding (quantity, total/average cost).</li>
 *   <li>Add a BOND BUY then a COUPON_INCOME and assert realized gain reflects the coupon
 *       while the open position (nominal/cost) is unchanged.</li>
 *   <li>Delete a transaction and assert the portfolio is recomputed from the remaining rows.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PortfolioTransactionFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AssetPriceQueryService assetPriceQueryService;

    @MockBean
    KeycloakUserAdminPort keycloakUserAdminPort;

    @MockBean
    KeycloakRegistrationFollowUpPort keycloakRegistrationFollowUpPort;

    @MockBean
    UserRegistrationPort userRegistrationPort;

    @MockBean
    UserAccountStatusPort userAccountStatusPort;

    private static final String USER = "flow-user-subject";

    @BeforeEach
    void setUp() {
        AssetPriceSnapshot mockSnapshot = new AssetPriceSnapshot(
                AssetType.STOCK,
                "THYAO.IS",
                new BigDecimal("100.00"),
                "TRY",
                LocalDateTime.now()
        );
        when(assetPriceQueryService.getCurrentPrice(any(), any())).thenReturn(mockSnapshot);
        when(userAccountStatusPort.isAccountEnabled(any())).thenReturn(true);
    }

    // =========================================================================
    // STOCK: create → buy x2 → holdings computed correctly
    // =========================================================================

    @Test
    void stockFlow_twoBuys_holdingAggregatesQuantityAndCost() throws Exception {
        String portfolioId = createPortfolio("Stock Flow Portfolio");

        // BUY 10 @ 150 (commission 0) -> cost 1500
        addTransaction(portfolioId, "THYAO.IS", "STOCK", "BUY",
                "10", "150.00", "0", "2026-01-15T10:00:00");
        // BUY 5 @ 200 (commission 0) -> cost 1000
        addTransaction(portfolioId, "THYAO.IS", "STOCK", "BUY",
                "5", "200.00", "0", "2026-02-15T10:00:00");

        JsonNode data = getPortfolio(portfolioId);
        JsonNode holding = findHolding(data, "THYAO.IS");

        // 15 toplam adet, toplam maliyet 2500, ortalama 166.6667
        assertThat(new BigDecimal(holding.path("totalQuantity").asText()))
                .isEqualByComparingTo("15");
        assertThat(new BigDecimal(holding.path("totalCost").asText()))
                .isEqualByComparingTo("2500");
        assertThat(new BigDecimal(holding.path("averageCost").asText()))
                .isEqualByComparingTo(new BigDecimal("166.6667"));
    }

    @Test
    void stockFlow_commissionIncludedInTotalCost() throws Exception {
        String portfolioId = createPortfolio("Stock Commission Portfolio");

        // BUY 10 @ 100 + commission 25 -> cost 1025
        addTransaction(portfolioId, "THYAO.IS", "STOCK", "BUY",
                "10", "100.00", "25.00", "2026-03-01T10:00:00");

        JsonNode data = getPortfolio(portfolioId);
        JsonNode holding = findHolding(data, "THYAO.IS");

        assertThat(new BigDecimal(holding.path("totalQuantity").asText()))
                .isEqualByComparingTo("10");
        assertThat(new BigDecimal(holding.path("totalCost").asText()))
                .isEqualByComparingTo("1025");
    }

    // =========================================================================
    // BOND: buy → coupon income → realized gain reflects coupon, position unchanged
    // =========================================================================

    @Test
    void bondFlow_couponIncome_addsRealizedGainWithoutChangingPosition() throws Exception {
        String portfolioId = createPortfolio("Bond Coupon Portfolio");

        // BOND BUY: qty 1000 @ price 95 (quoted per 100 nominal) -> cost = 1000 * 95/100 = 950
        addTransaction(portfolioId, "TRT240227T17", "BOND", "BUY",
                "1000", "95.00", "0", "2026-01-10T10:00:00");

        JsonNode beforeCoupon = getPortfolio(portfolioId);
        JsonNode bondBefore = findHolding(beforeCoupon, "TRT240227T17");
        assertThat(new BigDecimal(bondBefore.path("totalQuantity").asText()))
                .isEqualByComparingTo("1000");
        assertThat(new BigDecimal(bondBefore.path("totalCost").asText()))
                .isEqualByComparingTo("950");

        // Add a coupon income of 120 TL.
        addCouponIncome(portfolioId, "TRT240227T17", "120.00", "2026-03-10T10:00:00");

        JsonNode afterCoupon = getPortfolio(portfolioId);
        JsonNode bondAfter = findHolding(afterCoupon, "TRT240227T17");

        // Açık pozisyon (nominal + maliyet) DEĞİŞMEMELİ — kupon realized gelir.
        assertThat(new BigDecimal(bondAfter.path("totalQuantity").asText()))
                .isEqualByComparingTo("1000");
        assertThat(new BigDecimal(bondAfter.path("totalCost").asText()))
                .isEqualByComparingTo("950");

        // Kupon realized K/Z ve sumCouponIncome alanlarına yansır.
        assertThat(new BigDecimal(bondAfter.path("realizedGainLoss").asText()))
                .isEqualByComparingTo("120");
        assertThat(new BigDecimal(bondAfter.path("sumCouponIncome").asText()))
                .isEqualByComparingTo("120");

        // Portföy toplam realized K/Z da kuponu içermeli.
        assertThat(new BigDecimal(afterCoupon.path("totalRealizedProfitLoss").asText()))
                .isEqualByComparingTo("120");
    }

    // =========================================================================
    // DELETE: remove a transaction → portfolio recomputed from remaining rows
    // =========================================================================

    @Test
    void deleteTransaction_recomputesHoldingFromRemainingRows() throws Exception {
        String portfolioId = createPortfolio("Delete Recompute Portfolio");

        // Two BUYs; we will delete the second one.
        addTransaction(portfolioId, "THYAO.IS", "STOCK", "BUY",
                "10", "100.00", "0", "2026-01-15T10:00:00");
        JsonNode afterSecond = addTransaction(portfolioId, "THYAO.IS", "STOCK", "BUY",
                "20", "100.00", "0", "2026-02-15T10:00:00");

        // Combined: 30 qty, 3000 cost.
        JsonNode combinedHolding = findHolding(afterSecond, "THYAO.IS");
        assertThat(new BigDecimal(combinedHolding.path("totalQuantity").asText()))
                .isEqualByComparingTo("30");
        assertThat(new BigDecimal(combinedHolding.path("totalCost").asText()))
                .isEqualByComparingTo("3000");

        // Find the id of the 20-qty BUY to delete.
        String txnIdToDelete = null;
        for (JsonNode txn : afterSecond.path("transactions")) {
            if (new BigDecimal(txn.path("quantity").asText()).compareTo(new BigDecimal("20")) == 0) {
                txnIdToDelete = txn.path("id").asText();
            }
        }
        assertThat(txnIdToDelete).as("20-qty transaction id").isNotNull();

        String deleteResponse = mockMvc.perform(delete(
                        "/api/portfolios/" + portfolioId + "/transactions/" + txnIdToDelete)
                        .with(jwt(USER))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode afterDelete = objectMapper.readTree(deleteResponse).path("data");
        JsonNode remaining = findHolding(afterDelete, "THYAO.IS");

        // Yalnız ilk BUY kalmalı: 10 qty, 1000 cost.
        assertThat(new BigDecimal(remaining.path("totalQuantity").asText()))
                .isEqualByComparingTo("10");
        assertThat(new BigDecimal(remaining.path("totalCost").asText()))
                .isEqualByComparingTo("1000");

        // DB'de gerçekten tek transaction kalmalı (full-stack persistence doğrulaması).
        assertThat(afterDelete.path("transactions")).hasSize(1);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String createPortfolio(String name) throws Exception {
        String response = mockMvc.perform(post("/api/portfolios")
                        .with(jwt(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asText();
    }

    private JsonNode addTransaction(String portfolioId, String symbol, String assetType,
                                    String txnType, String quantity, String price,
                                    String commission, String txnDate) throws Exception {
        String body = String.format("""
                {
                  "symbol": "%s",
                  "assetType": "%s",
                  "transactionType": "%s",
                  "quantity": %s,
                  "price": %s,
                  "commission": %s,
                  "transactionDate": "%s"
                }
                """, symbol, assetType, txnType, quantity, price, commission, txnDate);

        String response = mockMvc.perform(post("/api/portfolios/" + portfolioId + "/transactions")
                        .with(jwt(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode addCouponIncome(String portfolioId, String symbol, String amount,
                                     String paymentDate) throws Exception {
        String body = String.format("""
                {
                  "symbol": "%s",
                  "amount": %s,
                  "paymentDate": "%s"
                }
                """, symbol, amount, paymentDate);

        String response = mockMvc.perform(post("/api/portfolios/" + portfolioId + "/coupon-income")
                        .with(jwt(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode getPortfolio(String portfolioId) throws Exception {
        String response = mockMvc.perform(get("/api/portfolios/" + portfolioId)
                        .with(jwt(USER))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode findHolding(JsonNode portfolioData, String symbol) {
        for (JsonNode holding : portfolioData.path("holdings")) {
            if (symbol.equals(holding.path("symbol").asText())) {
                return holding;
            }
        }
        throw new AssertionError("Holding not found for symbol: " + symbol
                + " in holdings=" + portfolioData.path("holdings"));
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt(String subject) {
        Jwt jwt = new Jwt(
                "mock-token-" + subject,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", subject,
                        "preferred_username", subject,
                        "email_verified", true,
                        "email", subject + "@example.com",
                        "realm_access", Map.of("roles", List.of("USER"))
                )
        );
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt);
    }
}
