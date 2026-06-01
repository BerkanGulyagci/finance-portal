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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * {@link PortfolioSummaryTool} ek dal kapsamı (PortfolioSummaryToolTest'i bozmadan): MAX_LINES taşması,
 * null assetType → "DİĞER", toplam maliyet=0 → yüzde yok, holding maliyeti=0 → satır yüzdesi yok,
 * toplam piyasa değeri=0 → dağılım yüzdesi atlanır, null holdings listesi.
 */
class PortfolioSummaryToolMoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PortfolioService portfolioService;
    private PortfolioSummaryTool tool;
    private final ToolContext authedCtx = new ToolContext("u1", "Berkan", "berkan@example.com");

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        tool = new PortfolioSummaryTool(portfolioService);
    }

    @Test
    @DisplayName("parameters() — required listesi boş (her şey opsiyonel)")
    @SuppressWarnings("unchecked")
    void parameters_noneRequired() {
        var schema = tool.parameters();
        assertThat((List<String>) schema.get("required")).isEmpty();
    }

    @Test
    @DisplayName("execute: null assetType → 'DİĞER' etiketi (typeTr default)")
    void execute_nullAssetType_diger() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Ana");
        p.setPortfolioType(PortfolioType.HOLDINGS);
        p.setHoldings(List.of(holding("???", null, "1000", "1200", "200")));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        String r = tool.execute(node("{\"detailed\":true}"), authedCtx);

        assertThat(r).contains("DİĞER").contains("???");
    }

    @Test
    @DisplayName("execute: toplam maliyet 0 → toplam K/Z yüzdesi gösterilmez")
    void execute_zeroTotalCost_noPercent() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Ana");
        p.setPortfolioType(PortfolioType.HOLDINGS);
        // totalCost 0, ama mv pozitif → dağılım çıkar, K/Z yüzdesi çıkmaz
        p.setHoldings(List.of(holding("BTC", AssetType.CRYPTO, "0", "500", "500")));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        String r = tool.execute(node("{}"), authedCtx);

        assertThat(r)
                .contains("Toplam değer: 500")
                .contains("Toplam maliyet: 0")
                .contains("Toplam K/Z: 500")
                .doesNotContain("%)");   // toplam K/Z yüzdesi yok (totalCost==0)
    }

    @Test
    @DisplayName("execute: holding maliyeti 0 → o satırda K/Z yüzdesi (plPct) yok, ama mv yüzdesi var")
    void execute_holdingZeroCost_noLinePercent() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Ana");
        p.setPortfolioType(PortfolioType.HOLDINGS);
        p.setHoldings(List.of(holding("AIRDROP", AssetType.CRYPTO, "0", "300", "300")));
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        String r = tool.execute(node("{\"detailed\":true}"), authedCtx);

        assertThat(r).contains("• AIRDROP").contains("K/Z 300 TL");
        // cost 0 → "(...%)" parantezli satır yüzdesi eklenmez
        assertThat(r).doesNotContain("%)");
    }

    @Test
    @DisplayName("execute: detailed=true + MAX_LINES (15) aşımı → '… (N varlık daha)' ile kesilir")
    void execute_detailedTruncatesAtMaxLines() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Buyuk");
        p.setPortfolioType(PortfolioType.HOLDINGS);
        List<PortfolioHoldingResponse> many = new ArrayList<>();
        // 18 varlık → 15 listelenir, 3 daha kalır
        for (int i = 0; i < 18; i++) {
            many.add(holding("SYM" + i, AssetType.STOCK,
                    "100", String.valueOf(1000 - i), "10"));   // azalan mv → sıralama belirgin
        }
        p.setHoldings(many);
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        String r = tool.execute(node("{\"detailed\":true}"), authedCtx);

        assertThat(r)
                .contains("Varlıklar:")
                .contains("… (3 varlık daha)");
        // en büyük mv (SYM0=1000) listenin başında olmalı
        assertThat(r.indexOf("• SYM0")).isLessThan(r.indexOf("… ("));
    }

    @Test
    @DisplayName("execute: portföyün holdings'i null → atlanır, sonuçta 'boş' mesajı")
    void execute_nullHoldingsList_treatedAsEmpty() {
        PortfolioResponse p = new PortfolioResponse();
        p.setName("Ana");
        p.setPortfolioType(PortfolioType.HOLDINGS);
        // holdings null bırakıldı
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(p));

        String r = tool.execute(node("{}"), authedCtx);

        assertThat(r).contains("Ana").contains("boş");
    }

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
