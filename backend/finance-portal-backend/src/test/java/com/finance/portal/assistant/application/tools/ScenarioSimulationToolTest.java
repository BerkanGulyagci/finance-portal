package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioCurrencyConverter;
import com.finance.portal.portfolio.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * simulate_portfolio_scenario: AI'nın tasarladığı şoku portföye DETERMİNİSTİK uygular.
 * Sembol > tip > ALL önceliği ve toplam yeni değer matematiğini doğrular.
 */
class ScenarioSimulationToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PortfolioService portfolioService;
    private ScenarioSimulationTool tool;
    private final ToolContext authed = new ToolContext("u1", "Berkan", "e@x.com");

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        PortfolioCurrencyConverter currencyConverter = mock(PortfolioCurrencyConverter.class);
        when(currencyConverter.toTry(any(), any())).thenAnswer(inv -> inv.getArgument(0)); // TRY identity
        when(portfolioService.getUserPortfolios(eq("u1"))).thenReturn(List.of(portfolio()));
        tool = new ScenarioSimulationTool(portfolioService, currencyConverter);
    }

    @Test
    @DisplayName("name/parameters — simulate_portfolio_scenario + shocks zorunlu")
    @SuppressWarnings("unchecked")
    void schema() {
        assertThat(tool.name()).isEqualTo("simulate_portfolio_scenario");
        Map<String, Object> schema = tool.parameters();
        assertThat((List<String>) schema.get("required")).contains("shocks");
    }

    @Test
    @DisplayName("Tip şoku: COMMODITY -%10 → sadece emtia düşer, toplam 1500→1400")
    void typeShock() {
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"COMMODITY\",\"change_percent\":-10}]}"), authed);
        assertThat(r).contains("1500").contains("1400").contains("Emtia").contains("WTI Ham Petrol");
    }

    @Test
    @DisplayName("Sembol şoku tip şokunu EZER: CL=F -%50 (COMMODITY -%10 verilse de) → WTI 1000→500, toplam 1000")
    void symbolOverridesType() {
        String r = tool.execute(node(
                "{\"shocks\":[{\"target\":\"COMMODITY\",\"change_percent\":-10},{\"target\":\"CL=F\",\"change_percent\":-50}]}"),
                authed);
        assertThat(r).contains("1000"); // 500 (WTI) + 500 (THYAO değişmedi)
    }

    @Test
    @DisplayName("ALL şoku tüm portföye uygulanır: -%20 → 1500→1200")
    void allShock() {
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-20}]}"), authed);
        assertThat(r).contains("1500").contains("1200");
    }

    @Test
    @DisplayName("Giriş yapılmamışsa uyarı döner")
    void notAuthenticated() {
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-20}]}"),
                new ToolContext(null, null, null));
        assertThat(r).contains("giriş");
    }

    @Test
    @DisplayName("Şok yoksa uyarı döner")
    void emptyShocks() {
        String r = tool.execute(node("{\"shocks\":[]}"), authed);
        assertThat(r).contains("en az bir şok");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PortfolioResponse portfolio() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Test Portföy"); // portfolioType null → WATCHLIST olmadığı için dahil edilir
        p.setHoldings(List.of(
                h("CL=F", "WTI Ham Petrol", AssetType.COMMODITY, "1000"),
                h("THYAO.IS", "Türk Hava Yolları", AssetType.STOCK, "500")));
        return p;
    }

    private PortfolioHoldingResponse h(String sym, String name, AssetType type, String mv) {
        PortfolioHoldingResponse x = new PortfolioHoldingResponse();
        x.setSymbol(sym);
        x.setName(name);
        x.setAssetType(type);
        x.setCurrency("TRY");
        x.setClosed(false);
        x.setMarketValue(new BigDecimal(mv));
        return x;
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
