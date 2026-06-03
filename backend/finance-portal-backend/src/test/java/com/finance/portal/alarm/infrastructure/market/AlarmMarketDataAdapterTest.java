package com.finance.portal.alarm.infrastructure.market;

import com.finance.portal.alarm.application.model.AlarmMarketSnapshot;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmMarketDataAdapterTest {

    @Mock StockQueryService stockQueryService;
    @Mock CryptoMarketService cryptoMarketService;
    @Mock AssetPriceQueryService assetPriceQueryService;
    @Mock GoldMarketService goldMarketService;
    @Mock YahooCommodityService yahooCommodityService;
    @Mock EvdsBondService evdsBondService;
    @Mock ViopService viopService;
    @Mock SilverMarketService silverMarketService;
    @Mock PreciousMetalService preciousMetalService;
    @Mock EurobondService eurobondService;

    private AlarmMarketDataAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AlarmMarketDataAdapter(stockQueryService, cryptoMarketService,
                assetPriceQueryService, goldMarketService, yahooCommodityService,
                evdsBondService, viopService, silverMarketService, preciousMetalService,
                eurobondService);
    }

    private static StockSummary stock(BigDecimal price, BigDecimal changePercent, Long volume) {
        StockSummary s = new StockSummary();
        s.setPrice(price);
        s.setChangePercent(changePercent);
        s.setVolume(volume);
        return s;
    }

    private static CryptoMarketItem crypto(BigDecimal price, BigDecimal change24h, BigDecimal volume) {
        return new CryptoMarketItem("bitcoin", "btc", "Bitcoin", null,
                price, null, null, volume, null, null, null, change24h, null, null, null);
    }

    @Test
    @DisplayName("Null/blank girdiler için probe null döner (erken çıkış)")
    void invalidInputs_returnNull() {
        assertThat(adapter.probe(null, "AKBNK")).isNull();
        assertThat(adapter.probe(AssetType.STOCK, null)).isNull();
        assertThat(adapter.probe(AssetType.STOCK, "   ")).isNull();
    }

    @Test
    @DisplayName("STOCK: özet bulunur → fiyat/değişim/hacim; null özet → null")
    void stock_happyAndNull() {
        when(stockQueryService.getStockSummary("AKBNK"))
                .thenReturn(stock(new BigDecimal("55.5"), new BigDecimal("1.25"), 1000L));

        AlarmMarketSnapshot snap = adapter.probe(AssetType.STOCK, "akbnk");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("55.5");
        assertThat(snap.changePercent()).isEqualByComparingTo("1.25");
        assertThat(snap.volume()).isEqualByComparingTo("1000");

        when(stockQueryService.getStockSummary("GARAN")).thenReturn(null);
        assertThat(adapter.probe(AssetType.STOCK, "GARAN")).isNull();
    }

    @Test
    @DisplayName("STOCK: hacim null ise snapshot hacmi null kalır")
    void stock_nullVolume() {
        when(stockQueryService.getStockSummary("ISCTR"))
                .thenReturn(stock(new BigDecimal("12.3"), new BigDecimal("-0.5"), null));

        AlarmMarketSnapshot snap = adapter.probe(AssetType.STOCK, "ISCTR");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("12.3");
        assertThat(snap.volume()).isNull();
    }

    @Test
    @DisplayName("FUTURE: VİOP kontratı (TR sayı formatı) parse edilir")
    void future_viopContract() {
        ViopContract c = new ViopContract();
        c.setLastPrice("1.234,56");
        c.setChangePercent("%1,23");
        when(viopService.findMatchingContract("F_XU0300625")).thenReturn(Optional.of(c));

        AlarmMarketSnapshot snap = adapter.probe(AssetType.FUTURE, "F_XU0300625");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("1234.56");
        assertThat(snap.changePercent()).isEqualByComparingTo("1.23");
        assertThat(snap.volume()).isNull();
    }

    @Test
    @DisplayName("FUTURE: VİOP yoksa stock summary fallback'ine düşer")
    void future_fallbackToStock() {
        when(viopService.findMatchingContract("ES=F")).thenReturn(Optional.empty());
        when(stockQueryService.getStockSummary("ES=F"))
                .thenReturn(stock(new BigDecimal("5000"), new BigDecimal("0.10"), 42L));

        AlarmMarketSnapshot snap = adapter.probe(AssetType.FUTURE, "ES=F");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("5000");
        assertThat(snap.volume()).isEqualByComparingTo("42");
    }

    @Test
    @DisplayName("CRYPTO: bulunur → fiyat/24s değişim/hacim; bulunmaz → null")
    void crypto_happyAndNull() {
        when(cryptoMarketService.findBySymbol("BTC"))
                .thenReturn(crypto(new BigDecimal("65000"), new BigDecimal("2.5"), new BigDecimal("123456")));

        AlarmMarketSnapshot snap = adapter.probe(AssetType.CRYPTO, "BTC");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("65000");
        assertThat(snap.changePercent()).isEqualByComparingTo("2.5");
        assertThat(snap.volume()).isEqualByComparingTo("123456");

        when(cryptoMarketService.findBySymbol("NOPE")).thenReturn(null);
        assertThat(adapter.probe(AssetType.CRYPTO, "NOPE")).isNull();
    }

    @Test
    @DisplayName("FX/FUND: AssetPriceQueryService'ten yalnız fiyat alınır; null → null")
    void fxAndFund_priceOnly() {
        when(assetPriceQueryService.getCurrentPrice(AssetType.FX, "USDTRY"))
                .thenReturn(new AssetPriceSnapshot(AssetType.FX, "USDTRY", new BigDecimal("32.10"), "TRY", null));

        AlarmMarketSnapshot fx = adapter.probe(AssetType.FX, "USDTRY");
        assertThat(fx).isNotNull();
        assertThat(fx.price()).isEqualByComparingTo("32.10");
        assertThat(fx.changePercent()).isNull();
        assertThat(fx.volume()).isNull();

        when(assetPriceQueryService.getCurrentPrice(AssetType.FUND, "TTE")).thenReturn(null);
        assertThat(adapter.probe(AssetType.FUND, "TTE")).isNull();
    }

    @Test
    @DisplayName("GOLD: GRAM gram fiyatı/değişim; GOLD ons fiyatı/onsChangePercent")
    void gold_symbolMapping() {
        GoldSpotResponse g = new GoldSpotResponse();
        g.setGramGoldTry(new BigDecimal("2500"));
        g.setOnsTry(new BigDecimal("78000"));
        g.setChangePercent(new BigDecimal("0.8"));
        g.setOnsChangePercent(new BigDecimal("1.1"));
        when(goldMarketService.getSpotGold()).thenReturn(g);

        AlarmMarketSnapshot gram = adapter.probe(AssetType.GOLD, "GRAM");
        assertThat(gram).isNotNull();
        assertThat(gram.price()).isEqualByComparingTo("2500");
        assertThat(gram.changePercent()).isEqualByComparingTo("0.8");

        AlarmMarketSnapshot ons = adapter.probe(AssetType.GOLD, "GOLD");
        assertThat(ons).isNotNull();
        assertThat(ons.price()).isEqualByComparingTo("78000");
        assertThat(ons.changePercent()).isEqualByComparingTo("1.1");
    }

    @Test
    @DisplayName("GOLD: spot null ise null döner")
    void gold_nullSpot() {
        when(goldMarketService.getSpotGold()).thenReturn(null);
        assertThat(adapter.probe(AssetType.GOLD, "GRAM")).isNull();
    }

    @Test
    @DisplayName("COMMODITY: düz emtia (displayPrice tercih), SILVER ve PLATINUM dallanması")
    void commodity_branches() {
        // Düz emtia: displayPrice öncelikli, hacim mevcut
        CommoditySpotDto c = new CommoditySpotDto();
        c.setDisplayPrice(new BigDecimal("75.5"));
        c.setRawPrice(new BigDecimal("70.0"));
        c.setChangePercent(new BigDecimal("0.3"));
        c.setVolume(999L);
        when(yahooCommodityService.getSpot("CL=F")).thenReturn(c);

        AlarmMarketSnapshot oil = adapter.probe(AssetType.COMMODITY, "CL=F");
        assertThat(oil).isNotNull();
        assertThat(oil.price()).isEqualByComparingTo("75.5");
        assertThat(oil.volume()).isEqualByComparingTo("999");

        // SILVER:KG_TRY → silver servisi
        SilverSpotResponse sp = new SilverSpotResponse();
        sp.setSilverGramTry(new BigDecimal("30"));
        sp.setCloseTryKg(new BigDecimal("30000"));
        sp.setSilverUsdOns(new BigDecimal("28"));
        when(silverMarketService.getSpotSilver()).thenReturn(sp);

        AlarmMarketSnapshot silver = adapter.probe(AssetType.COMMODITY, "SILVER:KG_TRY");
        assertThat(silver).isNotNull();
        assertThat(silver.price()).isEqualByComparingTo("30000");

        // PLATINUM:USD_ONS → kıymetli maden servisi
        PreciousMetalSpotResponse pm = new PreciousMetalSpotResponse();
        pm.setTryGram(new BigDecimal("1000"));
        pm.setUsdOns(new BigDecimal("950"));
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(pm);

        AlarmMarketSnapshot plat = adapter.probe(AssetType.COMMODITY, "PLATINUM:USD_ONS");
        assertThat(plat).isNotNull();
        assertThat(plat.price()).isEqualByComparingTo("950");
    }

    @Test
    @DisplayName("COMMODITY: silver default gram, displayPrice null ise rawPrice kullanılır")
    void commodity_silverDefaultAndRawPrice() {
        SilverSpotResponse sp = new SilverSpotResponse();
        sp.setSilverGramTry(new BigDecimal("30"));
        when(silverMarketService.getSpotSilver()).thenReturn(sp);

        AlarmMarketSnapshot silverGram = adapter.probe(AssetType.COMMODITY, "SILVER");
        assertThat(silverGram).isNotNull();
        assertThat(silverGram.price()).isEqualByComparingTo("30");

        CommoditySpotDto c = new CommoditySpotDto();
        c.setDisplayPrice(null);
        c.setRawPrice(new BigDecimal("66.0"));
        c.setVolume(null);
        when(yahooCommodityService.getSpot("NG=F")).thenReturn(c);

        AlarmMarketSnapshot gas = adapter.probe(AssetType.COMMODITY, "NG=F");
        assertThat(gas).isNotNull();
        assertThat(gas.price()).isEqualByComparingTo("66.0");
        assertThat(gas.volume()).isNull();
    }

    @Test
    @DisplayName("BOND: Eurobond ISIN'i ise EurobondService; aksi halde EVDS DİBS")
    void bond_eurobondAndEvds() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40"));
        when(eurobondService.currentPrice("US900123AL40")).thenReturn(new BigDecimal("98.75"));

        AlarmMarketSnapshot euro = adapter.probe(AssetType.BOND, "us900123al40");
        assertThat(euro).isNotNull();
        assertThat(euro.price()).isEqualByComparingTo("98.75");
        assertThat(euro.changePercent()).isNull();

        EvdsBondInstrument b = new EvdsBondInstrument();
        b.setIndicatorValue(new BigDecimal("105.4"));
        b.setDailyChangePercent(new BigDecimal("-0.2"));
        when(evdsBondService.getEvdsBondDetail("TRT060127T10")).thenReturn(b);

        AlarmMarketSnapshot dibs = adapter.probe(AssetType.BOND, "TRT060127T10");
        assertThat(dibs).isNotNull();
        assertThat(dibs.price()).isEqualByComparingTo("105.4");
        assertThat(dibs.changePercent()).isEqualByComparingTo("-0.2");
    }

    @Test
    @DisplayName("Bağımlılık exception fırlatırsa probe null döner (try/catch ile yutulur)")
    void dependencyThrows_gracefulNull() {
        when(stockQueryService.getStockSummary(anyString()))
                .thenThrow(new RuntimeException("upstream down"));

        assertThat(adapter.probe(AssetType.STOCK, "AKBNK")).isNull();
    }

    @Test
    @DisplayName("BOND: EVDS detay null ise null döner")
    void bond_evdsNull() {
        when(eurobondService.currentIsins()).thenReturn(List.of());
        when(evdsBondService.getEvdsBondDetail(any())).thenReturn(null);

        assertThat(adapter.probe(AssetType.BOND, "TRT060127T10")).isNull();
    }
}
