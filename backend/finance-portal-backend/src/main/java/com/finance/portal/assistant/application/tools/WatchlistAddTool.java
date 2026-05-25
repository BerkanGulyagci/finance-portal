package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.presentation.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bir varlığı kullanıcının izleme listesine (favori) ekler (giriş + ONAY gerekli). İzleme listesi yoksa
 * "Favoriler" adıyla oluşturur. confirm=false iken eklemez; LLM önce onay alır, sonra confirm=true ile çağırır.
 */
@Component
public class WatchlistAddTool implements AssistantTool {

    private final PortfolioService portfolioService;

    public WatchlistAddTool(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Override
    public String name() {
        return "add_to_watchlist";
    }

    @Override
    public String description() {
        return "Bir varlığı kullanıcının izleme listesine (favori) ekler (giriş gerekli). "
                + "asset_type + symbol (get_current_price kuralları). ÖNEMLİ: önce confirm OLMADAN çağır → "
                + "kullanıcıya 'X'i favorilere ekleyeyim mi?' diye sor; SADECE onaylayınca confirm=true ile tekrar çağır.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "asset_type", Map.of("type", "string",
                                "enum", List.of("STOCK", "CRYPTO", "FX", "GOLD", "COMMODITY", "BOND", "FUND", "FUTURE")),
                        "symbol", Map.of("type", "string", "description", "Sembol (get_current_price kuralları)"),
                        "confirm", Map.of("type", "boolean", "description", "Kullanıcı onayladıysa true")),
                "required", List.of("asset_type", "symbol"));
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        if (ctx == null || !ctx.isAuthenticated()) {
            return "Favorilere eklemek için kullanıcının giriş yapması gerekir.";
        }
        String assetTypeStr = text(args, "asset_type").toUpperCase(Locale.ROOT);
        String symbol = text(args, "symbol").trim().toUpperCase(Locale.ROOT);
        if (symbol.isBlank() || assetTypeStr.isBlank()) {
            return "Eksik bilgi: varlık türü ve sembol gerekli.";
        }
        AssetType assetType;
        try {
            assetType = AssetType.valueOf(assetTypeStr);
        } catch (Exception e) {
            return "Geçersiz varlık türü: " + assetTypeStr;
        }
        boolean confirm = boolArg(args, "confirm");
        if (!confirm) {
            return "ONAY_BEKLENIYOR: " + symbol + " (" + assetTypeStr + ") favorilere/izleme listesine eklenecek. "
                    + "Kullanıcıya sor; onaylarsa confirm=true ile tekrar çağır.";
        }

        try {
            UUID watchlistId = resolveWatchlistId(ctx.userId());
            AddWatchlistItemRequest req = new AddWatchlistItemRequest();
            req.setSymbol(symbol);
            req.setAssetType(assetType);
            req.setNotes("Porti ile eklendi");
            portfolioService.addWatchlistItem(ctx.userId(), watchlistId, req);
            return symbol + " favorilere (izleme listesi) eklendi.";
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.toLowerCase(Locale.ROOT).contains("already") || msg.contains("zaten") || msg.contains("exist")) {
                return symbol + " zaten favorilerinde.";
            }
            return "Favorilere eklenemedi: " + msg;
        }
    }

    /** İlk izleme listesi; yoksa "Favoriler" adıyla oluşturur. */
    private UUID resolveWatchlistId(String userId) {
        List<PortfolioResponse> all = portfolioService.getUserPortfolios(userId);
        for (PortfolioResponse p : all) {
            if (p.getPortfolioType() == PortfolioType.WATCHLIST) {
                return p.getId();
            }
        }
        CreatePortfolioRequest cr = new CreatePortfolioRequest();
        cr.setName("Favoriler");
        cr.setPortfolioType("WATCHLIST");
        return portfolioService.createPortfolio(userId, cr).getId();
    }

    private static String text(JsonNode args, String field) {
        JsonNode n = args != null ? args.get(field) : null;
        return n != null && !n.isNull() ? n.asText("") : "";
    }

    private static boolean boolArg(JsonNode args, String field) {
        JsonNode n = args != null ? args.get(field) : null;
        return n != null && !n.isNull() && n.asBoolean(false);
    }
}
