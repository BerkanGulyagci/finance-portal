package com.finance.portal.alarm.infrastructure.market;

import com.finance.portal.alarm.application.model.AlarmMarketSnapshot;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * AlarmMarketDataAdapterTest'in atladığı dalları kapsayan ek testler:
 * gold/silver/precious switch kollarının her arm'ı, VİOP fiyatı parse edilemeyince
 * stock fallback, parseTrNumber'ın null/blank/virgülsüz/NFE kolları, catOf iki-nokta
 * sonrası boş, eurobond fiyatı null, commodity yahoo null + PALLADIUM dallanması.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlarmMarketDataAdapterMoreTest {

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

    /** Tüm gram/çeyrek/yarım/ziynet/cumhuriyet/ayar ve ons değişim alanları dolu bir gold spot. */
    private static GoldSpotResponse fullGold() {
        GoldSpotResponse g = new GoldSpotResponse();
        g.setGramGoldTry(new BigDecimal("2500"));
        g.setOnsTry(new BigDecimal("78000"));
        g.setQuarterGoldTry(new BigDecimal("4100"));
        g.setHalfGoldTry(new BigDecimal("8200"));
        g.setZiynetGoldTry(new BigDecimal("16400"));
        g.setRepublicGoldTry(new BigDecimal("16800"));
        g.setFourteenKBraceletTry(new BigDecimal("1460"));
        g.setTwentyTwoKBraceletTry(new BigDecimal("2290"));
        g.setChangePercent(new BigDecimal("0.8"));
        g.setOnsChangePercent(new BigDecimal("1.1"));
        return g;
    }

    // ── FUTURE: VİOP fiyatı parse edilemiyor → stock fallback ─────────────────

    @Test
    @DisplayName("FUTURE: VİOP lastPrice parse edilemez (virgülsüz, NFE) → stock fallback (null)")
    void future_viopUnparsablePrice_fallsBackToStock() {
        ViopContract c = new ViopContract();
        c.setLastPrice("-");          // virgül yok → new BigDecimal('-') → NumberFormatException → null
        c.setChangePercent("%0,0");
        when(viopService.findMatchingContract("F_BADC")).thenReturn(Optional.of(c));
        when(stockQueryService.getStockSummary("F_BADC")).thenReturn(null);

        // price == null kolu (if price != null FALSE) + parseTrNumber NFE kolu + fromStock fallback
        assertThat(adapter.probe(AssetType.FUTURE, "F_BADC")).isNull();
    }

    @Test
    @DisplayName("FUTURE: VİOP virgülsüz tam sayı fiyatı parse edilir, changePercent blank → null")
    void future_viopIntegerPrice_blankChange() {
        ViopContract c = new ViopContract();
        c.setLastPrice("5000");       // virgülsüz başarı kolu
        c.setChangePercent("   ");    // parseTrNumber blank kolu → null
        when(viopService.findMatchingContract("F_OK")).thenReturn(Optional.of(c));

        AlarmMarketSnapshot snap = adapter.probe(AssetType.FUTURE, "F_OK");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("5000");
        assertThat(snap.changePercent()).isNull();
        assertThat(snap.volume()).isNull();
    }

    @Test
    @DisplayName("FUTURE: VİOP changePercent null → parseTrNumber null-guard kolu")
    void future_viopNullChange() {
        ViopContract c = new ViopContract();
        c.setLastPrice("100");
        c.setChangePercent(null);     // parseTrNumber raw == null kolu
        when(viopService.findMatchingContract("F_NULLPCT")).thenReturn(Optional.of(c));

        AlarmMarketSnapshot snap = adapter.probe(AssetType.FUTURE, "F_NULLPCT");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("100");
        assertThat(snap.changePercent()).isNull();
    }

    // ── GOLD: switch'in kapsanmamış kolları ───────────────────────────────────

    @Test
    @DisplayName("GOLD: ONS → onsTry + onsChangePercent (|| sağ operandı)")
    void gold_onsSymbol() {
        when(goldMarketService.getSpotGold()).thenReturn(fullGold());

        AlarmMarketSnapshot ons = adapter.probe(AssetType.GOLD, "ONS");
        assertThat(ons).isNotNull();
        assertThat(ons.price()).isEqualByComparingTo("78000");
        assertThat(ons.changePercent()).isEqualByComparingTo("1.1");
    }

    @Test
    @DisplayName("GOLD: CEYREK/YARIM kolları")
    void gold_ceyrekYarim() {
        when(goldMarketService.getSpotGold()).thenReturn(fullGold());

        assertThat(adapter.probe(AssetType.GOLD, "CEYREK").price()).isEqualByComparingTo("4100");
        assertThat(adapter.probe(AssetType.GOLD, "YARIM").price()).isEqualByComparingTo("8200");
    }

    @Test
    @DisplayName("GOLD: ZIYNET/TAM (aynı kol) ve CUMHUR/ATA/CUMHURIYET kolları")
    void gold_ziynetAndRepublic() {
        when(goldMarketService.getSpotGold()).thenReturn(fullGold());

        assertThat(adapter.probe(AssetType.GOLD, "ZIYNET").price()).isEqualByComparingTo("16400");
        assertThat(adapter.probe(AssetType.GOLD, "TAM").price()).isEqualByComparingTo("16400");
        assertThat(adapter.probe(AssetType.GOLD, "CUMHUR").price()).isEqualByComparingTo("16800");
        assertThat(adapter.probe(AssetType.GOLD, "ATA").price()).isEqualByComparingTo("16800");
        assertThat(adapter.probe(AssetType.GOLD, "CUMHURIYET").price()).isEqualByComparingTo("16800");
    }

    @Test
    @DisplayName("GOLD: 14AYAR/AYAR14 ve 22AYAR/AYAR22 kolları")
    void gold_braceletArms() {
        when(goldMarketService.getSpotGold()).thenReturn(fullGold());

        assertThat(adapter.probe(AssetType.GOLD, "14AYAR").price()).isEqualByComparingTo("1460");
        assertThat(adapter.probe(AssetType.GOLD, "AYAR14").price()).isEqualByComparingTo("1460");
        assertThat(adapter.probe(AssetType.GOLD, "22AYAR").price()).isEqualByComparingTo("2290");
        assertThat(adapter.probe(AssetType.GOLD, "AYAR22").price()).isEqualByComparingTo("2290");
    }

    @Test
    @DisplayName("GOLD: bilinmeyen sembol → default kolu (gramGoldTry) + changePercent kolu")
    void gold_unknownSymbolDefault() {
        when(goldMarketService.getSpotGold()).thenReturn(fullGold());

        AlarmMarketSnapshot snap = adapter.probe(AssetType.GOLD, "WHATEVER");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("2500");      // default → gramGoldTry
        assertThat(snap.changePercent()).isEqualByComparingTo("0.8"); // ternary FALSE kolu
    }

    // ── COMMODITY: silver/precious switch kolları + yahoo null + PALLADIUM ─────

    @Test
    @DisplayName("COMMODITY: SILVER:USD_ONS kolu")
    void commodity_silverUsdOns() {
        SilverSpotResponse sp = new SilverSpotResponse();
        sp.setSilverGramTry(new BigDecimal("30"));
        sp.setCloseTryKg(new BigDecimal("30000"));
        sp.setSilverUsdOns(new BigDecimal("28.5"));
        when(silverMarketService.getSpotSilver()).thenReturn(sp);

        AlarmMarketSnapshot snap = adapter.probe(AssetType.COMMODITY, "SILVER:USD_ONS");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("28.5");
        assertThat(snap.changePercent()).isNull();
    }

    @Test
    @DisplayName("COMMODITY: silver spot null → null")
    void commodity_silverNull() {
        when(silverMarketService.getSpotSilver()).thenReturn(null);
        assertThat(adapter.probe(AssetType.COMMODITY, "SILVER:KG_TRY")).isNull();
    }

    @Test
    @DisplayName("COMMODITY: SILVER: (iki nokta sonrası boş) → catOf default GRAM_TRY")
    void commodity_silverColonEmpty_catOfDefault() {
        SilverSpotResponse sp = new SilverSpotResponse();
        sp.setSilverGramTry(new BigDecimal("31"));
        when(silverMarketService.getSpotSilver()).thenReturn(sp);

        // "SILVER:" → indexOf(':')=6, i+1==length → catOf FALSE kolu → "GRAM_TRY"
        AlarmMarketSnapshot snap = adapter.probe(AssetType.COMMODITY, "SILVER:");
        assertThat(snap).isNotNull();
        assertThat(snap.price()).isEqualByComparingTo("31");
    }

    @Test
    @DisplayName("COMMODITY: PALLADIUM dallanması + KG_TRY/EUR_ONS/default(tryGram) kolları")
    void commodity_palladiumArms() {
        PreciousMetalSpotResponse pm = new PreciousMetalSpotResponse();
        pm.setTryGram(new BigDecimal("3300"));
        pm.setTryKg(new BigDecimal("3300000"));
        pm.setUsdOns(new BigDecimal("1000"));
        pm.setEurOns(new BigDecimal("930"));
        when(preciousMetalService.getSpot(PreciousMetalType.PALLADIUM)).thenReturn(pm);

        assertThat(adapter.probe(AssetType.COMMODITY, "PALLADIUM:KG_TRY").price())
                .isEqualByComparingTo("3300000");
        assertThat(adapter.probe(AssetType.COMMODITY, "PALLADIUM:EUR_ONS").price())
                .isEqualByComparingTo("930");
        // iki nokta yok → catOf "GRAM_TRY" → switch default → tryGram
        assertThat(adapter.probe(AssetType.COMMODITY, "PALLADIUM").price())
                .isEqualByComparingTo("3300");
    }

    @Test
    @DisplayName("COMMODITY: precious metal spot null → null")
    void commodity_preciousNull() {
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(null);
        assertThat(adapter.probe(AssetType.COMMODITY, "PLATINUM:KG_TRY")).isNull();
    }

    @Test
    @DisplayName("COMMODITY: PLATINUM KG_TRY kolu")
    void commodity_platinumKgTry() {
        PreciousMetalSpotResponse pm = new PreciousMetalSpotResponse();
        pm.setTryKg(new BigDecimal("1500000"));
        pm.setTryGram(new BigDecimal("1500"));
        when(preciousMetalService.getSpot(PreciousMetalType.PLATINUM)).thenReturn(pm);

        assertThat(adapter.probe(AssetType.COMMODITY, "PLATINUM:KG_TRY").price())
                .isEqualByComparingTo("1500000");
    }

    @Test
    @DisplayName("COMMODITY: yahoo getSpot null → null")
    void commodity_yahooNull() {
        when(yahooCommodityService.getSpot("HG=F")).thenReturn(null);
        assertThat(adapter.probe(AssetType.COMMODITY, "HG=F")).isNull();
    }

    // ── BOND: eurobond ISIN var ama fiyat null → ternary FALSE → null ─────────

    @Test
    @DisplayName("BOND: Eurobond ISIN'i ama currentPrice null → null")
    void bond_eurobondPriceNull() {
        when(eurobondService.currentIsins()).thenReturn(List.of("US900123AL40"));
        when(eurobondService.currentPrice("US900123AL40")).thenReturn(null);

        assertThat(adapter.probe(AssetType.BOND, "US900123AL40")).isNull();
    }
}
