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

class CurrentPriceToolTest {

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

    // ------------------------------ metadata ------------------------------

    @Test
    @DisplayName("name() — get_current_price")
    void schemaConstants() {
        assertThat(tool.name()).isEqualTo("get_current_price");
    }

    // ------------------------------ validation ------------------------------

    @Test
    @DisplayName("execute: boş symbol → 'Sembol belirtilmedi'")
    void execute_blankSymbol_rejected() {
        String r = tool.execute(node("{\"asset_type\":\"FX\",\"symbol\":\"\"}"), null);

        assertThat(r).contains("Sembol belirtilmedi");
        verifyNoInteractions(fx, crypto, gold, stock, silver, yahoo);
    }

    @Test
    @DisplayName("execute: bilinmeyen asset_type → 'Desteklenmeyen tür'")
    void execute_unsupportedType_complains() {
        String r = tool.execute(node("{\"asset_type\":\"WEIRD\",\"symbol\":\"X\"}"), null);

        assertThat(r).contains("Desteklenmeyen tür").contains("WEIRD");
    }

    // ------------------------------ FX ------------------------------

    @Test
    @DisplayName("FX: USD bulunduğunda TCMB satış kuru ile döner")
    void fx_usd_returnsSellPrice() {
        FxRateItem usd = new FxRateItem("USD", new BigDecimal("45.20"), new BigDecimal("45.27"), 1);
        when(fx.getTcmbLatestRates("USD"))
                .thenReturn(new FxLatestRates(null, null, null, "2026-05-26", List.of(usd)));

        String r = tool.execute(node("{\"asset_type\":\"FX\",\"symbol\":\"USD\"}"), null);

        assertThat(r).contains("1 USD = 45.27 TL").contains("2026-05-26");
    }

    @Test
    @DisplayName("FX: JPY unit=100 → kur unit'e bölünür")
    void fx_jpyUnit100_divides() {
        FxRateItem jpy = new FxRateItem("JPY", new BigDecimal("27.50"), new BigDecimal("28.00"), 100);
        when(fx.getTcmbLatestRates("JPY"))
                .thenReturn(new FxLatestRates(null, null, null, "2026-05-26", List.of(jpy)));

        String r = tool.execute(node("{\"asset_type\":\"FX\",\"symbol\":\"JPY\"}"), null);

        // 28.00 / 100 = 0.28
        assertThat(r).contains("1 JPY = 0.28 TL");
    }

    @Test
    @DisplayName("FX: sembol bulunamaz → 'TCMB kuru bulunamadı'")
    void fx_symbolNotFound_message() {
        when(fx.getTcmbLatestRates("EUR"))
                .thenReturn(new FxLatestRates(null, null, null, null, List.of()));

        String r = tool.execute(node("{\"asset_type\":\"FX\",\"symbol\":\"EUR\"}"), null);

        assertThat(r).contains("EUR").contains("TCMB kuru bulunamadı");
    }

    // ------------------------------ CRYPTO ------------------------------

    @Test
    @DisplayName("CRYPTO: BTC bulunduğunda fiyat + 24h değişim döner")
    void crypto_btc_priceAndChange() {
        // CryptoMarketItem all-args ctor: (id, symbol, name, image, currentPrice, marketCap,
        //   marketCapRank, totalVolume, high24h, low24h, priceChange24h,
        //   priceChangePercentage24h, priceChangePercentage1h, priceChangePercentage7d, lastUpdated)
        CryptoMarketItem btc = new CryptoMarketItem(
                "bitcoin", "BTC", "Bitcoin", null,
                new BigDecimal("2400000"), null, null, null, null, null, null,
                new BigDecimal("3.5"), null, null, null);
        when(crypto.findBySymbol("BTC")).thenReturn(btc);

        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"BTC\"}"), null);

        assertThat(r).contains("Bitcoin (BTC)").contains("2400000").contains("3.5%");
    }

    @Test
    @DisplayName("CRYPTO: bulunamaz → 'kripto fiyatı bulunamadı'")
    void crypto_notFound_message() {
        when(crypto.findBySymbol(anyString())).thenReturn(null);

        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"XYZ\"}"), null);

        assertThat(r).contains("XYZ").contains("kripto fiyatı bulunamadı");
    }

    // ------------------------------ GOLD ------------------------------

    @Test
    @DisplayName("GOLD: GRAM → 'Gram altın'")
    void gold_gram() {
        GoldSpotResponse s = new GoldSpotResponse();
        s.setGramGoldTry(new BigDecimal("3060.19"));
        when(gold.getSpotGold()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"GRAM\"}"), null);

        assertThat(r).contains("Gram altın").contains("3060.19").contains("TL");
    }

    @Test
    @DisplayName("GOLD: CEYREK → 'Çeyrek altın'")
    void gold_ceyrek() {
        GoldSpotResponse s = new GoldSpotResponse();
        s.setQuarterGoldTry(new BigDecimal("5000"));
        when(gold.getSpotGold()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"CEYREK\"}"), null);

        assertThat(r).contains("Çeyrek altın").contains("5000");
    }

    @Test
    @DisplayName("GOLD: tanınmayan sembol → 'Altın türü tanınmadı'")
    void gold_unknownSymbol_message() {
        when(gold.getSpotGold()).thenReturn(new GoldSpotResponse());

        String r = tool.execute(node("{\"asset_type\":\"GOLD\",\"symbol\":\"UFO\"}"), null);

        assertThat(r).contains("Altın türü tanınmadı").contains("UFO");
    }

    // ------------------------------ STOCK ------------------------------

    @Test
    @DisplayName("STOCK: BIST hissesi → fiyat + günlük değişim, .IS suffix temizlenir")
    void stock_bist_returnsPriceAndChange() {
        StockSummary s = new StockSummary();
        s.setSymbol("THYAO.IS");
        s.setPrice(new BigDecimal("305.5"));
        s.setChangePercent(new BigDecimal("2.1"));
        s.setCurrency("TRY");
        when(stock.getStockSummary("THYAO")).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\"}"), null);

        assertThat(r).startsWith("THYAO = 305.5 TRY").contains("günlük 2.1%");
        assertThat(r).doesNotContain(".IS");
    }

    @Test
    @DisplayName("STOCK: bulunamaz → 'BIST fiyatı bulunamadı'")
    void stock_notFound_message() {
        when(stock.getStockSummary(anyString())).thenReturn(null);

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"YOK\"}"), null);

        assertThat(r).contains("YOK").contains("BIST fiyatı bulunamadı");
    }

    // ------------------------------ COMMODITY ------------------------------

    @Test
    @DisplayName("COMMODITY: GUMUS → silver service'i çağır")
    void commodity_silver_callsSilverService() {
        SilverSpotResponse s = new SilverSpotResponse();
        s.setSilverGramTry(new BigDecimal("38.25"));
        when(silver.getSpotSilver()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"GUMUS\"}"), null);

        assertThat(r).contains("Gram gümüş").contains("38.25");
        verifyNoInteractions(yahoo);
    }

    @Test
    @DisplayName("COMMODITY: SILVER alias → silver service")
    void commodity_silverAlias_callsSilverService() {
        SilverSpotResponse s = new SilverSpotResponse();
        s.setSilverGramTry(new BigDecimal("40"));
        when(silver.getSpotSilver()).thenReturn(s);

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"SILVER\"}"), null);

        assertThat(r).contains("Gram gümüş");
    }

    @Test
    @DisplayName("COMMODITY: Yahoo emtiası (ör. NG=F) → yahoo service")
    void commodity_yahoo_returnsSpot() {
        CommoditySpotDto spot = new CommoditySpotDto();
        spot.setDisplayPrice(new BigDecimal("2.96"));
        spot.setDisplayCurrency("USD");
        spot.setDisplayNameTr("Doğal Gaz");
        when(yahoo.getSpot("NG=F")).thenReturn(spot);

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"NG=F\"}"), null);

        assertThat(r).contains("Doğal Gaz").contains("2.96").contains("USD");
    }

    @Test
    @DisplayName("COMMODITY: emtia bulunamaz → '... için emtia fiyatı bulunamadı'")
    void commodity_yahooNull_message() {
        when(yahoo.getSpot(anyString())).thenReturn(null);

        String r = tool.execute(node("{\"asset_type\":\"COMMODITY\",\"symbol\":\"X\"}"), null);

        assertThat(r).contains("X").contains("emtia fiyatı bulunamadı");
    }

    // ------------------------------ error path ------------------------------

    @Test
    @DisplayName("execute: servis exception fırlatırsa → 'anlık fiyat alınamadı'")
    void execute_serviceThrows_friendlyMessage() {
        when(crypto.findBySymbol(anyString())).thenThrow(new RuntimeException("upstream down"));

        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"BTC\"}"), null);

        assertThat(r).contains("BTC").contains("anlık fiyat alınamadı");
    }

    private static JsonNode node(String json) {
        try { return MAPPER.readTree(json); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
