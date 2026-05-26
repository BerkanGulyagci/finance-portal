package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.presentation.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WatchlistAddToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PortfolioService portfolioService;
    private WatchlistAddTool tool;
    private final ToolContext authedCtx = new ToolContext("u1", "Berkan", "berkan@example.com");

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        tool = new WatchlistAddTool(portfolioService);
    }

    // ------------------------------ metadata ------------------------------

    @Test
    @DisplayName("name() / description() — tool şema sabitleri + ONAY vurgusu")
    void schemaConstants() {
        assertThat(tool.name()).isEqualTo("add_to_watchlist");
        assertThat(tool.description())
                .contains("favori")
                .contains("confirm");
    }

    // ------------------------------ auth gate ------------------------------

    @Test
    @DisplayName("execute: anon ctx → giriş gerek mesajı")
    void execute_anonContext_blocked() {
        ToolContext anon = new ToolContext(null, null, null);

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\"}"), anon);

        assertThat(r).contains("giriş yapması");
        verifyNoInteractions(portfolioService);
    }

    @Test
    @DisplayName("execute: null ctx → giriş gerek")
    void execute_nullContext_blocked() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\"}"), null);

        assertThat(r).contains("giriş yapması");
        verifyNoInteractions(portfolioService);
    }

    // ------------------------------ validation ------------------------------

    @Test
    @DisplayName("execute: eksik symbol → 'Eksik bilgi'")
    void execute_missingSymbol_complains() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\"}"), authedCtx);

        assertThat(r).contains("Eksik bilgi");
        verifyNoInteractions(portfolioService);
    }

    @Test
    @DisplayName("execute: geçersiz asset_type → açıklayıcı hata")
    void execute_invalidAssetType_rejected() {
        String r = tool.execute(node("{\"asset_type\":\"GARBAGE\",\"symbol\":\"THYAO\"}"), authedCtx);

        assertThat(r).contains("Geçersiz varlık türü");
        verifyNoInteractions(portfolioService);
    }

    // ------------------------------ onay akışı ------------------------------

    @Test
    @DisplayName("execute: confirm=false → ONAY_BEKLENIYOR, portfolioService çağrılmaz")
    void execute_unconfirmed_returnsPending() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"thyao\"}"), authedCtx);

        assertThat(r).startsWith("ONAY_BEKLENIYOR").contains("THYAO");  // uppercase
        verifyNoInteractions(portfolioService);
    }

    @Test
    @DisplayName("execute: confirm alanı yok → onay bekleniyor (varsayılan false)")
    void execute_noConfirm_treatedAsFalse() {
        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"BTC\"}"), authedCtx);

        assertThat(r).startsWith("ONAY_BEKLENIYOR");
        verifyNoInteractions(portfolioService);
    }

    // ------------------------------ mevcut watchlist'i çöz ------------------------------

    @Test
    @DisplayName("execute: mevcut WATCHLIST varsa o id kullanılır (yeni oluşturmaz)")
    void execute_existingWatchlist_isReused() {
        UUID existingId = UUID.randomUUID();
        PortfolioResponse existing = new PortfolioResponse();
        existing.setId(existingId);
        existing.setPortfolioType(PortfolioType.WATCHLIST);
        PortfolioResponse holdings = new PortfolioResponse();
        holdings.setId(UUID.randomUUID());
        holdings.setPortfolioType(PortfolioType.HOLDINGS);
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(holdings, existing));

        ArgumentCaptor<AddWatchlistItemRequest> reqCap = ArgumentCaptor.forClass(AddWatchlistItemRequest.class);

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"thyao\",\"confirm\":true}"), authedCtx);

        verify(portfolioService).addWatchlistItem(eq("u1"), eq(existingId), reqCap.capture());
        verify(portfolioService, never()).createPortfolio(anyString(), any());
        AddWatchlistItemRequest req = reqCap.getValue();
        assertThat(req.getSymbol()).isEqualTo("THYAO");
        assertThat(req.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(req.getNotes()).contains("Porti");
        assertThat(r).contains("favorilere").contains("eklendi");
    }

    @Test
    @DisplayName("execute: WATCHLIST yoksa 'Favoriler' otomatik oluşturulur ve oraya eklenir")
    void execute_noWatchlist_createsOneNamedFavoriler() {
        // Kullanıcının hiç WATCHLIST'i yok
        PortfolioResponse holdings = new PortfolioResponse();
        holdings.setId(UUID.randomUUID());
        holdings.setPortfolioType(PortfolioType.HOLDINGS);
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(holdings));

        UUID newWlId = UUID.randomUUID();
        PortfolioResponse created = new PortfolioResponse();
        created.setId(newWlId);
        created.setPortfolioType(PortfolioType.WATCHLIST);
        when(portfolioService.createPortfolio(eq("u1"), any(CreatePortfolioRequest.class)))
                .thenReturn(created);

        ArgumentCaptor<CreatePortfolioRequest> createCap = ArgumentCaptor.forClass(CreatePortfolioRequest.class);

        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"btc\",\"confirm\":true}"), authedCtx);

        verify(portfolioService).createPortfolio(eq("u1"), createCap.capture());
        assertThat(createCap.getValue().getName()).isEqualTo("Favoriler");
        assertThat(createCap.getValue().getPortfolioType()).isEqualTo("WATCHLIST");
        verify(portfolioService).addWatchlistItem(eq("u1"), eq(newWlId), any(AddWatchlistItemRequest.class));
        assertThat(r).contains("favorilere").contains("BTC");
    }

    // ------------------------------ error paths ------------------------------

    @Test
    @DisplayName("execute: 'already exists' hatası → kullanıcı dostu mesaj")
    void execute_alreadyExistsError_friendlyMessage() {
        UUID wlId = UUID.randomUUID();
        PortfolioResponse wl = new PortfolioResponse();
        wl.setId(wlId);
        wl.setPortfolioType(PortfolioType.WATCHLIST);
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(wl));
        doThrow(new RuntimeException("Symbol already in watchlist"))
                .when(portfolioService).addWatchlistItem(any(), any(), any());

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"confirm\":true}"), authedCtx);

        assertThat(r).contains("zaten favorilerinde");
    }

    @Test
    @DisplayName("execute: 'zaten ekli' Türkçe error → kullanıcı dostu mesaj")
    void execute_zatenError_friendlyMessage() {
        UUID wlId = UUID.randomUUID();
        PortfolioResponse wl = new PortfolioResponse();
        wl.setId(wlId);
        wl.setPortfolioType(PortfolioType.WATCHLIST);
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(wl));
        doThrow(new RuntimeException("Bu sembol listede zaten var"))
                .when(portfolioService).addWatchlistItem(any(), any(), any());

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"AKBNK\",\"confirm\":true}"), authedCtx);

        assertThat(r).contains("zaten favorilerinde");
    }

    @Test
    @DisplayName("execute: generic exception → 'Favorilere eklenemedi' + sebep")
    void execute_genericError_returnsErrorMessage() {
        UUID wlId = UUID.randomUUID();
        PortfolioResponse wl = new PortfolioResponse();
        wl.setId(wlId);
        wl.setPortfolioType(PortfolioType.WATCHLIST);
        when(portfolioService.getUserPortfolios("u1")).thenReturn(List.of(wl));
        doThrow(new RuntimeException("db down"))
                .when(portfolioService).addWatchlistItem(any(), any(), any());

        String r = tool.execute(node("{\"asset_type\":\"FX\",\"symbol\":\"USD\",\"confirm\":true}"), authedCtx);

        assertThat(r).startsWith("Favorilere eklenemedi").contains("db down");
    }

    // ------------------------------ helper ------------------------------

    private static JsonNode node(String json) {
        try { return MAPPER.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
