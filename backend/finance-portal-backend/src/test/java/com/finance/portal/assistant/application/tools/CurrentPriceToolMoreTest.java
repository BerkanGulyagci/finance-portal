package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link CurrentPriceTool} ek dal kapsamı (CurrentPriceToolTest'i bozmadan): gold ONS/YARIM/TAM/CUMHUR
 * etiketleri, FX unit≤1 dalı, stock null currency/null değişim, crypto null isim/null değişim,
 * commodity Yahoo null fiyat + displayNameEn/symbol fallback + null currency, gümüş close fallback / null.
 */
class CurrentPriceToolMoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MarketFxService fx;
    private CryptoMarketService crypto;
    private GoldMarketService gold;
    private StockQueryService stock;
    private SilverMarketService silver;
    private YahooCommodityService yahoo;
    private CurrentPriceTool tool;

    @BeforeEach
    void setUp() {
        fx = mock(MarketFxService.class);
        crypto = mock(CryptoMarketService.class);
        gold = mock(GoldMarketService.class);
        stock = mock(StockQueryService.class);
        silver = mock(SilverMarketService.class);
        yahoo = mock(YahooCommodityService.class);
        tool = new CurrentPriceTool(fx, crypto, gold, stock, silver, yahoo);
    }

    // ------------------------------ metadata / şema ------------------------------

    @Test
    @DisplayName("description() / parameters() — enum + required alanlar")
    @SuppressWarnings("unchecked")
    void schema() {
        assertThat(tool.description()).contains("COMMODITY").contains("GUMUS");
        var schema = tool.parameters();
        assertThat((List<String>) schema.get("required")).containsExactlyInAnyOrder("asset_type", "symbol");
    }

    // ------------------------------ FX unit ≤ 1 dalı ------------------------------

    @Test
    @DisplayName("FX: unit=0 → birim 1 kabul edilir, satış kuru bölünmeden döner")
    void fx_unitZero_treatedAsOne() {
        FxRateItem usd = new FxRateItem("USD", new BigDecimal("45.20"), new BigDecimal("45.27"), 0);
        when(fx.getTcmbLatestRates("USD"))
                .thenReturn(new FxLatestRates(null, null, null, "2026-06-01", List.of(usd)));

        String r = tool.execute(node("{\"asset_type\":\"FX\",\"symbol\":\"USD\"}"), null);

        assertThat(r).contains("1 USD = 45.27 TL").contains("2026-06-01");
    }

    @Test
    @DisplayName("FX: sell null → 'TCMB kuru bulunamadı'")
    void fx_sellNull_message() {
        FxRateItem eur = new FxRateItem("EUR", new BigDecimal("48"), null, 1);
        when(fx.getTcmbLatestRates("EUR"))
                .thenReturn(new FxLatestRates(null, null, null, "2026-06-01", List.of(eur)));

        String r = tool.execute(node("{\"asset_type\":\"FX\",\"symbol\":\"EUR\"}"), null);

        assertThat(r).contains("EUR").contains("TCMB kuru bulunamadı");
    }

    // ------------------------------ GOLD etiketleri ------------------------------

    @Test
    @DisplayName("GOLD: ONS → 'Ons altın'")
    void gold_ons() {
        GoldSpotResponse s = new GoldSpotResponse();
        s.setOnsTry(new BigDecimal("105000"));
        when(gold.getSpotGold()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"ONS\"}"), null);

        assertThat(r).contains("Ons altın").contains("105000");
    }

    @Test
    @DisplayName("GOLD: YARIM → 'Yarım altın'")
    void gold_yarim() {
        GoldSpotResponse s = new GoldSpotResponse();
        s.setHalfGoldTry(new BigDecimal("10000"));
        when(gold.getSpotGold()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"YARIM\"}"), null);

        assertThat(r).contains("Yarım altın").contains("10000");
    }

    @Test
    @DisplayName("GOLD: TAM/ZIYNET → 'Tam altın'")
    void gold_tam() {
        GoldSpotResponse s = new GoldSpotResponse();
        s.setZiynetGoldTry(new BigDecimal("20000"));
        when(gold.getSpotGold()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"ZIYNET\"}"), null);

        assertThat(r).contains("Tam altın").contains("20000");
    }

    @Test
    @DisplayName("GOLD: CUMHUR/ATA → 'Cumhuriyet altını'")
    void gold_cumhur() {
        GoldSpotResponse s = new GoldSpotResponse();
        s.setRepublicGoldTry(new BigDecimal("21000"));
        when(gold.getSpotGold()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"ATA\"}"), null);

        assertThat(r).contains("Cumhuriyet altını").contains("21000");
    }

    @Test
    @DisplayName("GOLD: bilinen sembol ama fiyat null → 'Altın türü tanınmadı veya fiyat yok'")
    void gold_knownSymbolNullPrice() {
        // GRAM sembolü tanınır ama gramGoldTry set edilmemiş (null) → price==null dalı
        when(gold.getSpotGold()).thenReturn(new GoldSpotResponse());

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"GRAM\"}"), null);

        assertThat(r).contains("fiyat yok").contains("GRAM");
    }

    // ------------------------------ STOCK null alanlar ------------------------------

    @Test
    @DisplayName("STOCK: currency null → TRY varsayılır, changePercent null → değişim eklenmez")
    void stock_nullCurrencyAndChange_defaults() {
        StockSummary s = new StockSummary();
        s.setSymbol("AKBNK");
        s.setPrice(new BigDecimal("65.4"));
        // currency ve changePercent null
        when(stock.getStockSummary("AKBNK")).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"AKBNK\"}"), null);

        assertThat(r).contains("AKBNK = 65.4 TRY").doesNotContain("günlük");
    }

    @Test
    @DisplayName("STOCK: price null → 'BIST fiyatı bulunamadı'")
    void stock_nullPrice_message() {
        StockSummary s = new StockSummary();
        s.setSymbol("YOK");
        // price null
        when(stock.getStockSummary("YOK")).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"YOK\"}"), null);

        assertThat(r).contains("YOK").contains("BIST fiyatı bulunamadı");
    }

    // ------------------------------ CRYPTO null alanlar ------------------------------

    @Test
    @DisplayName("CRYPTO: name null → sembol kullanılır, 24h null → değişim eklenmez")
    void crypto_nullNameAndChange_usesSymbol() {
        CryptoMarketItem item = new CryptoMarketItem(
                "x", "XRP", null, null,
                new BigDecimal("18.5"), null, null, null, null, null, null,
                null, null, null, null);
        when(crypto.findBySymbol("XRP")).thenReturn(item);

        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"XRP\"}"), null);

        assertThat(r).contains("XRP (XRP) = 18.5 TL").doesNotContain("son 24 saat");
    }

    @Test
    @DisplayName("CRYPTO: currentPrice null → 'kripto fiyatı bulunamadı'")
    void crypto_nullPrice_message() {
        CryptoMarketItem item = new CryptoMarketItem(
                "x", "DOGE", "Dogecoin", null,
                null, null, null, null, null, null, null,
                null, null, null, null);
        when(crypto.findBySymbol("DOGE")).thenReturn(item);

        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"DOGE\"}"), null);

        assertThat(r).contains("DOGE").contains("kripto fiyatı bulunamadı");
    }

    // ------------------------------ COMMODITY (gümüş + yahoo) ------------------------------

    @Test
    @DisplayName("COMMODITY: gümüş silverGramTry null → silverGramCloseTry fallback kullanılır")
    void commodity_silverCloseFallback() {
        SilverSpotResponse s = new SilverSpotResponse();
        // silverGramTry null → close fallback
        s.setSilverGramCloseTry(new BigDecimal("37.10"));
        when(silver.getSpotSilver()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"GÜMÜŞ\"}"), null);

        assertThat(r).contains("Gram gümüş").contains("37.1");
        verifyNoInteractions(yahoo);
    }

    @Test
    @DisplayName("COMMODITY: gümüş her iki fiyat da null → 'Gümüş fiyatı bulunamadı'")
    void commodity_silverBothNull_message() {
        when(silver.getSpotSilver()).thenReturn(new SilverSpotResponse());

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"SILVER:XAG\"}"), null);

        assertThat(r).contains("Gümüş fiyatı bulunamadı");
    }

    @Test
    @DisplayName("COMMODITY: Yahoo displayPrice null → 'emtia fiyatı bulunamadı'")
    void commodity_yahooNullPrice_message() {
        CommoditySpotDto spot = new CommoditySpotDto();
        // displayPrice null
        when(yahoo.getSpot("CL=F")).thenReturn(spot);

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"CL=F\"}"), null);

        assertThat(r).contains("CL=F").contains("emtia fiyatı bulunamadı");
    }

    @Test
    @DisplayName("COMMODITY: displayNameTr null → displayNameEn fallback, displayCurrency null → USD")
    void commodity_displayNameEnFallback_usdDefault() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("2000"));
        spot.setDisplayNameEn("Gold");
        // displayNameTr null, displayCurrency null
        when(yahoo.getSpot("GC=F")).thenReturn(spot);

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"GC=F\"}"), null);

        assertThat(r).contains("Gold = 2000 USD");
    }

    @Test
    @DisplayName("COMMODITY: displayNameTr ve En null → sembol fallback isim")
    void commodity_symbolFallbackName() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("4.5"));
        spot.setDisplayCurrency("USD");
        // her iki isim de null
        when(yahoo.getSpot("HG=F")).thenReturn(spot);

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"HG=F\"}"), null);

        assertThat(r).contains("HG=F = 4.5 USD");
    }

    // ------------------------------ null args / text helper ------------------------------

    @Test
    @DisplayName("execute: null args → boş symbol → 'Sembol belirtilmedi'")
    void execute_nullArgs_blankSymbol() {
        String r = tool.execute(null, null);

        assertThat(r).contains("Sembol belirtilmedi");
        verifyNoInteractions(fx, crypto, gold, stock, silver, yahoo);
    }

    @Test
    @DisplayName("execute: STOCK servis exception → 'anlık fiyat alınamadı'")
    void execute_stockThrows_friendly() {
        when(stock.getStockSummary(anyString())).thenThrow(new RuntimeException("down"));

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\"}"), null);

        assertThat(r).contains("THYAO").contains("anlık fiyat alınamadı").contains("STOCK");
    }

    private static JsonNode node(String json) {
        try { return MAPPER.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
