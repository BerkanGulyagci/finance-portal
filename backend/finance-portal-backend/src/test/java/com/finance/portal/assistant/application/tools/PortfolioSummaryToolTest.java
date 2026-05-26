package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PortfolioSummaryToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PortfolioService portfolioService;
    private PortfolioSummaryTool tool;
    private final ToolContext authedCtx = new ToolContext("u1", "Berkan", "berkan@example.com");

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        tool = new PortfolioSummaryTool(portfolioService);
    }

    // ------------------------------ metadata ------------------------------

    @Test
    @DisplayName("name() — get_portfolio_summary")
    void schemaConstants() {
        assertThat(tool.name()).isEqualTo("get_portfolio_summary");
        assertThat(tool.description()).contains("portföy").contains("detailed");
    }

    // ------------------------------ auth gate ------------------------------

    @Test
    @DisplayName("execute: anon ctx → giriş gerek")
    void execute_anon_blocked() {
        String r = tool.execute(node("{}"), new ToolContext(null, null, null));

        assertThat(r).contains("giriş yapmamış");
        verifyNoInteractions(portfolioService);
    }

    @Test
    @DisplayName("execute: null ctx → giriş gerek")
    void execute_nullCtx_blocked() {
        String r = tool.execute(node("{}"), null);

        assertThat(r).contains("giriş yapmamış");
        verifyNoInteractions(portfolioService);
    }

    // ------------------------------ no portföy ------------------------------

    @Test
    @DisplayName("execute: kullanıcının yalnızca WATCHLIST portföyü var → 'portföyü yok'")
    void execute_onlyWatchlist_noHoldingsPortfolio() {
        PortfolioResponse wl = new PortfolioResponse();
        wl.setId(UUID.randomUUID());
        wl.setName("Favoriler");
        wl.setPortfolioType(PortfolioType.WATCHLIST);
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(wl));

        String r = tool.execute(node("{}"), authedCtx);

        assertThat(r).contains("portföyü yok");
    }

    @Test
    @DisplayName("execute: HOLDINGS portföy var ama varlık yok → 'boş'")
    void execute_holdingsPortfolio_butNoHoldings() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Ana");
        p.setPortfolioType(PortfolioType.HOLDINGS);
        p.setHoldings(List.of());
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        String r = tool.execute(node("{}"), authedCtx);

        assertThat(r).contains("Ana").contains("boş");
    }

    // ------------------------------ varsayılan (detailed=false) ------------------------------

    @Test
    @DisplayName("execute: tek portföy, varlıklar var, detailed=false → toplam + dağılım + DÖKÜM_YOK işareti")
    void execute_detailedFalse_summaryOnly_withMarker() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Ana");
        p.setPortfolioType(PortfolioType.HOLDINGS);
        p.setHoldings(List.of(
                holding("THYAO", AssetType.STOCK, "1000", "1500", "500"),
                holding("XAU", AssetType.GOLD, "2000", "2200", "200")
        ));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        String r = tool.execute(node("{}"), authedCtx);

        // Toplam K/Z = 500+200 = 700; %= 700/3000=23.33
        assertThat(r)
                .contains("\"Ana\" portföyü")
                .contains("Toplam değer: 3700")
                .contains("Toplam maliyet: 3000")
                .contains("Toplam K/Z: 700")
                .contains("23.33%")
                .contains("Varlık türü dağılımı")
                .contains("Hisse").contains("Altın")
                .contains("[DÖKÜM_YOK")                 // marker, kullanıcıya detayı sor
                .doesNotContain("Varlıklar:\n• THYAO"); // detay yok
    }

    @Test
    @DisplayName("execute: birden çok HOLDINGS portföyü birleşik özetlenir")
    void execute_multiplePortfolios_combined() {
        PortfolioResponse p1 = new PortfolioResponse();
        p1.setName("Ana");
        p1.setPortfolioType(PortfolioType.HOLDINGS);
        p1.setHoldings(List.of(holding("THYAO", AssetType.STOCK, "1000", "1100", "100")));
        PortfolioResponse p2 = new PortfolioResponse();
        p2.setName("Yedek");
        p2.setPortfolioType(PortfolioType.HOLDINGS);
        p2.setHoldings(List.of(holding("BTC", AssetType.CRYPTO, "500", "600", "100")));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p1, p2));

        String r = tool.execute(node("{}"), authedCtx);

        assertThat(r)
                .contains("2 portföy birleşik")
                .contains("Toplam değer: 1700")     // 1100 + 600
                .contains("Hisse").contains("Kripto");
    }

    // ------------------------------ portfolio_name filtresi ------------------------------

    @Test
    @DisplayName("execute: portfolio_name tam eşleşme → yalnız o portföy + diğerlerin adı eklenir")
    void execute_exactNameMatch_filtersAndMentionsOthers() {
        PortfolioResponse ana = new PortfolioResponse();
        ana.setName("Ana");
        ana.setPortfolioType(PortfolioType.HOLDINGS);
        ana.setHoldings(List.of(holding("THYAO", AssetType.STOCK, "1000", "1200", "200")));
        PortfolioResponse yedek = new PortfolioResponse();
        yedek.setName("Yedek");
        yedek.setPortfolioType(PortfolioType.HOLDINGS);
        yedek.setHoldings(List.of(holding("BTC", AssetType.CRYPTO, "500", "600", "100")));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(ana, yedek));

        String r = tool.execute(node("{\"portfolio_name\":\"Ana\"}"), authedCtx);

        assertThat(r)
                .contains("\"Ana\" portföyü")
                .contains("Toplam değer: 1200")     // sadece Ana
                .doesNotContain("Toplam değer: 1800")
                .contains("Diğer portföyler: Yedek");
    }

    @Test
    @DisplayName("execute: portfolio_name kısmi eşleşme (lowercase contains) → eşleşen ilk portföy")
    void execute_partialNameMatch_caseInsensitive() {
        PortfolioResponse ana = new PortfolioResponse();
        ana.setName("Ana Portföy");
        ana.setPortfolioType(PortfolioType.HOLDINGS);
        ana.setHoldings(List.of(holding("THYAO", AssetType.STOCK, "1000", "1200", "200")));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(ana));

        String r = tool.execute(node("{\"portfolio_name\":\"ana\"}"), authedCtx);

        assertThat(r).contains("\"Ana Portföy\"");
    }

    @Test
    @DisplayName("execute: portfolio_name eşleşmiyor → hangisini analiz edeyim diye sor")
    void execute_nameNotFound_listsAvailable() {
        PortfolioResponse ana = new PortfolioResponse();
        ana.setName("Ana");
        ana.setPortfolioType(PortfolioType.HOLDINGS);
        ana.setHoldings(List.of(holding("THYAO", AssetType.STOCK, "1000", "1200", "200")));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(ana));

        String r = tool.execute(node("{\"portfolio_name\":\"YokOlan\"}"), authedCtx);

        assertThat(r)
                .contains("YokOlan").contains("bulunamadı")
                .contains("Ana")                     // mevcut portföy adları
                .contains("Hangisini analiz edeyim");
    }

    // ------------------------------ detailed=true ------------------------------

    @Test
    @DisplayName("execute: detailed=true → 'Varlıklar:' bölümü var, marker yok, mv'ye göre sıralı")
    void execute_detailedTrue_showsHoldings() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Ana");
        p.setPortfolioType(PortfolioType.HOLDINGS);
        p.setHoldings(List.of(
                holding("THYAO", AssetType.STOCK, "1000", "1500", "500"),     // mv 1500
                holding("BTC", AssetType.CRYPTO, "100", "300", "200"),         // mv 300
                holding("XAU", AssetType.GOLD, "500", "550", "50")              // mv 550
        ));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        String r = tool.execute(node("{\"detailed\":true}"), authedCtx);

        assertThat(r).contains("Varlıklar:").doesNotContain("[DÖKÜM_YOK");
        // Sıralama: THYAO (1500) → XAU (550) → BTC (300)
        int iThyao = r.indexOf("• THYAO");
        int iXau = r.indexOf("• XAU");
        int iBtc = r.indexOf("• BTC");
        assertThat(iThyao).isLessThan(iXau);
        assertThat(iXau).isLessThan(iBtc);
        // Her satırda K/Z yüzdesi
        assertThat(r).contains("50%");                                // THYAO: 500/1000
    }

    // ------------------------------ hata ------------------------------

    @Test
    @DisplayName("execute: portfolioService exception → 'Portföy verisi alınamadı.'")
    void execute_serviceThrows_friendlyMessage() {
        when(portfolioService.getUserPortfolios("u1"))
                .thenThrow(new RuntimeException("db down"));

        String r = tool.execute(node("{}"), authedCtx);

        assertThat(r).contains("Portföy verisi alınamadı");
    }

    // ------------------------------ helper ------------------------------

    private static PortfolioHoldingResponse holding(String symbol, AssetType type,
                                                    String cost, String mv, String pl) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setAssetType(type);
        h.setTotalCost(new BigDecimal(cost));
        h.setMarketValue(new BigDecimal(mv));
        h.setProfitLoss(new BigDecimal(pl));
        return h;
    }

    private static JsonNode node(String json) {
        try { return MAPPER.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
