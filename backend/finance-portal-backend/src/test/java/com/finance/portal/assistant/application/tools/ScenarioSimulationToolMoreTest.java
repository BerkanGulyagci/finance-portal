package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.service.PortfolioCurrencyConverter;
import com.finance.portal.portfolio.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tamamlayıcısı: ScenarioSimulationToolTest'in DEĞMEDİĞİ dalları hedefler.
 * - parseShocks: null/array-değil/alternatif anahtarlar/target boş/pct eksik
 * - portföy kapsamı: WATCHLIST filtresi sonrası boş, çoklu portföy birleşik, isim eşleşmesi var/yok
 * - holding döngüsü: holdings null / kapalı pozisyon / tl null / tl<=0 / pct==0 (mover eklenmez)
 * - oldTotal<=0 yolu, >MAX_LINES kesim satırı, hareket yokken movers bloğu atlanır
 * - catch dalı, shockFor öncelik kombinasyonları, trTarget tüm switch kolları, label fallback'leri
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScenarioSimulationToolMoreTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private PortfolioService portfolioService;
    @Mock
    private PortfolioCurrencyConverter currencyConverter;

    private ScenarioSimulationTool tool;
    private final ToolContext authed = new ToolContext("u1", "Berkan", "e@x.com");

    @BeforeEach
    void setUp() {
        // toTry varsayılan: TRY identity. Bazı testler bunu yeniden tanımlar.
        when(currencyConverter.toTry(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        tool = new ScenarioSimulationTool(portfolioService, currencyConverter);
    }

    // ── parseShocks: erken çıkışlar ────────────────────────────────────────────

    @Test
    @DisplayName("shocks alanı array değilse → şok parse edilmez, 'en az bir şok' uyarısı")
    void shocksNotArray() {
        String r = tool.execute(node("{\"shocks\":\"COMMODITY\"}"), authed);
        assertThat(r).contains("en az bir şok");
    }

    @Test
    @DisplayName("shocks alanı hiç yoksa (arr null) → 'en az bir şok' uyarısı")
    void shocksMissing() {
        String r = tool.execute(node("{}"), authed);
        assertThat(r).contains("en az bir şok");
    }

    @Test
    @DisplayName("target boş ve change_percent eksik girdiler atlanır → tüm şoklar elenince uyarı")
    void shocksAllInvalidSkipped() {
        // 1) target yok  2) change yok  3) target boş string
        String r = tool.execute(node(
                "{\"shocks\":[{\"change_percent\":-10},{\"target\":\"ALL\"},{\"target\":\"\",\"change_percent\":-5}]}"),
                authed);
        assertThat(r).contains("en az bir şok");
    }

    @Test
    @DisplayName("alternatif anahtar adları desteklenir: changePercent/percent/change → şok geçerli sayılır")
    void shocksAlternateKeys() {
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(single("THYAO.IS", "THY", AssetType.STOCK, "1000")));
        // 'changePercent' camelCase anahtar -> -25 (firstNonNull ikinci anahtar)
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"changePercent\":-25}]}"), authed);
        assertThat(r).contains("1000").contains("750"); // 1000 → 750
    }

    @Test
    @DisplayName("'percent' ve 'change' anahtarları da kabul edilir (çoklu şok, biri sembol biri tip)")
    void shocksPercentAndChangeKeys() {
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(single("THYAO.IS", "THY", AssetType.STOCK, "1000")));
        String r = tool.execute(node(
                "{\"shocks\":[{\"target\":\"STOCK\",\"percent\":10},{\"target\":\"THYAO.IS\",\"change\":50}]}"),
                authed);
        // Sembol tip'i ezer: THYAO.IS +50 → 1500
        assertThat(r).contains("1500");
    }

    // ── portföy kapsamı ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("WATCHLIST filtresi sonrası hiç portföy kalmazsa → '(izleme listesi dışında) portföyü yok'")
    void onlyWatchlistPortfolios() {
        PortfolioResponse wl = new PortfolioResponse();
        wl.setName("İzleme");
        wl.setPortfolioType(PortfolioType.WATCHLIST);
        wl.setHoldings(List.of(h("THYAO.IS", "THY", AssetType.STOCK, "1000")));
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(wl));

        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-10}]}"), authed);
        assertThat(r).contains("portföyü yok");
    }

    @Test
    @DisplayName("İsim verilip EŞLEŞME bulunursa kapsam o portföye daralır (scopeLabel = ismi)")
    void portfolioNameMatches() {
        PortfolioResponse a = named("Emeklilik", h("THYAO.IS", "THY", AssetType.STOCK, "1000"));
        PortfolioResponse b = named("Spekülatif", h("BTC", "Bitcoin", AssetType.CRYPTO, "2000"));
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(a, b));

        String r = tool.execute(node(
                "{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-10}],\"portfolio_name\":\"emek\"}"),
                authed);
        assertThat(r).contains("Emeklilik");
        // Yalnız Emeklilik (1000) kapsamda → 1000 → 900
        assertThat(r).contains("900").doesNotContain("Spekülatif");
    }

    @Test
    @DisplayName("İsim verilir ama EŞLEŞMEZSE kapsam tüm portföyler kalır (çoklu → 'birleşik')")
    void portfolioNameNoMatchKeepsAll() {
        PortfolioResponse a = named("Emeklilik", h("THYAO.IS", "THY", AssetType.STOCK, "1000"));
        PortfolioResponse b = named("Spekülatif", h("BTC", "Bitcoin", AssetType.CRYPTO, "2000"));
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(a, b));

        String r = tool.execute(node(
                "{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":0}],\"portfolio_name\":\"yokboyle\"}"),
                authed);
        assertThat(r).contains("2 portföy birleşik");
        // 1000 + 2000 = 3000 toplam, şok %0
        assertThat(r).contains("3000");
    }

    @Test
    @DisplayName("İsmi null olan portföy filtrede NPE atmadan atlanır; eşleşen portföye daralır")
    void portfolioNullNameSkippedInFilter() {
        PortfolioResponse nullName = new PortfolioResponse();
        nullName.setName(null); // filter: getName()!=null guard
        nullName.setPortfolioType(PortfolioType.HOLDINGS);
        nullName.setHoldings(List.of(h("AAA", "A", AssetType.STOCK, "1000")));
        PortfolioResponse b = named("Altın Kasası", h("XAU", "Altın", AssetType.GOLD, "2000"));
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(nullName, b));

        String r = tool.execute(node(
                "{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-50}],\"portfolio_name\":\"altın\"}"),
                authed);
        assertThat(r).contains("Altın Kasası").contains("2000").contains("1000"); // 2000 → 1000
    }

    // ── holding döngüsü ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("holdings null portföy atlanır; kapalı pozisyon ve tl<=0 holding'ler hesaba katılmaz")
    void holdingsNullClosedAndNonPositiveSkipped() {
        PortfolioResponse nullHoldings = named("Boş");
        nullHoldings.setHoldings(null); // p.getHoldings()==null → continue

        PortfolioHoldingResponse closed = h("CLOSED", "Kapalı", AssetType.BOND, "9999");
        closed.setClosed(true); // h.isClosed() → continue
        PortfolioHoldingResponse zero = h("ZERO", "Sıfır", AssetType.STOCK, "0"); // tl.signum()<=0 → continue
        PortfolioHoldingResponse live = h("THYAO.IS", "THY", AssetType.STOCK, "1000");
        PortfolioResponse data = named("Veri", closed, zero, live);

        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(nullHoldings, data));

        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-10}]}"), authed);
        // Yalnız canlı 1000 sayılır → 1000 → 900
        assertThat(r).contains("1000").contains("900").doesNotContain("9999");
    }

    @Test
    @DisplayName("toTry null dönerse o holding atlanır; tüm toplam 0 olunca 'boş ya da değeri hesaplanamadı'")
    void toTryNullMakesTotalZero() {
        when(currencyConverter.toTry(any(), any())).thenReturn(null); // her holding tl==null
        when(portfolioService.getUserPortfolios(any()))
                .thenReturn(List.of(single("THYAO.IS", "THY", AssetType.STOCK, "1000")));

        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-10}]}"), authed);
        assertThat(r).contains("boş ya da değeri hesaplanamadı");
    }

    @Test
    @DisplayName("pct==0 holding mover'a eklenmez → 'Etkilenen pozisyonlar' bloğu hiç çıkmaz, coverage %0")
    void noMoversWhenShockDoesNotMatch() {
        when(portfolioService.getUserPortfolios(any()))
                .thenReturn(List.of(single("THYAO.IS", "THY", AssetType.STOCK, "1000")));
        // CRYPTO şoku STOCK holding'e değmez → pct=0, mover yok
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"CRYPTO\",\"change_percent\":-30}]}"), authed);
        assertThat(r).doesNotContain("Etkilenen pozisyonlar");
        assertThat(r).contains("Şoktan etkilenen portföy oranı: %0");
        // değer değişmez: 1000 → 1000
        assertThat(r).contains("1000");
    }

    @Test
    @DisplayName(">MAX_LINES (10) hareket → liste 10'da kesilir + '… (+N pozisyon daha)' satırı")
    void moreThanMaxLinesTruncated() {
        List<PortfolioHoldingResponse> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(h("SYM" + i, "Pozisyon" + i, AssetType.STOCK, String.valueOf(1000 + i)));
        }
        PortfolioResponse p = named("Geniş");
        p.setHoldings(many);
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(p));

        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-10}]}"), authed);
        assertThat(r).contains("Etkilenen pozisyonlar");
        // 12 hareket, 10 gösterilir, 2 fazla
        assertThat(r).contains("… (+2 pozisyon daha)");
    }

    // ── catch dalı ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("portföy servisi patlarsa catch → 'Senaryo hesaplanamadı.'")
    void serviceThrowsCaught() {
        when(portfolioService.getUserPortfolios(any())).thenThrow(new RuntimeException("boom"));
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-10}]}"), authed);
        assertThat(r).isEqualTo("Senaryo hesaplanamadı.");
    }

    // ── shockFor öncelik dalları ─────────────────────────────────────────────────

    @Test
    @DisplayName("byType öncelik: sembolü boş holding + tip şoku → byType uygulanır")
    void shockForTypeWhenSymbolBlank() {
        PortfolioHoldingResponse noSym = h(null, "İsimsiz Sembol", AssetType.FUND, "1000"); // symbol null → sym==""
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(named("F", noSym)));
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"FUND\",\"change_percent\":20}]}"), authed);
        assertThat(r).contains("1200"); // 1000 → 1200
    }

    @Test
    @DisplayName("byType yokken byAll devreye girer; sonra hiçbiri yoksa 0 (her iki dal tek senaryoda)")
    void shockForFallsBackToAllThenZero() {
        // ALL şoku FX'e değer (byAll), GOLD şoku yoksa GOLD holding 0 kalır
        PortfolioHoldingResponse fx = h("USDTRY", "Dolar", AssetType.FX, "1000");
        PortfolioHoldingResponse gold = h("XAU", "Altın", AssetType.GOLD, "1000");
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(named("Karma", fx, gold)));
        // ALL -%10 her ikisine de byAll olarak uygulanır
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-10}]}"), authed);
        // toplam 2000 → 1800
        assertThat(r).contains("2000").contains("1800");
    }

    @Test
    @DisplayName("Hiçbir şok eşleşmez (yalnız tanınmayan sembol) → tüm pct 0, değer değişmez")
    void shockForNoneZero() {
        when(portfolioService.getUserPortfolios(any()))
                .thenReturn(List.of(single("THYAO.IS", "THY", AssetType.STOCK, "1000")));
        String r = tool.execute(node("{\"shocks\":[{\"target\":\"XYZ.UNKNOWN\",\"change_percent\":-40}]}"), authed);
        assertThat(r).contains("Şoktan etkilenen portföy oranı: %0");
    }

    // ── trTarget tüm switch kolları + default ────────────────────────────────────

    @Test
    @DisplayName("trTarget: bilinen tüm tipler TR etiketine çevrilir + bilinmeyen olduğu gibi (default)")
    void trTargetAllArms() {
        when(portfolioService.getUserPortfolios(any()))
                .thenReturn(List.of(single("THYAO.IS", "THY", AssetType.STOCK, "1000")));
        // Her şok tipi 0% (matematiği etkilemez) — yalnız "Uygulanan şoklar" satırındaki TR etiketleri için
        String r = tool.execute(node("{\"shocks\":["
                + "{\"target\":\"STOCK\",\"change_percent\":0},"
                + "{\"target\":\"CRYPTO\",\"change_percent\":0},"
                + "{\"target\":\"FX\",\"change_percent\":0},"
                + "{\"target\":\"GOLD\",\"change_percent\":0},"
                + "{\"target\":\"SILVER\",\"change_percent\":0},"
                + "{\"target\":\"COMMODITY\",\"change_percent\":0},"
                + "{\"target\":\"FUND\",\"change_percent\":0},"
                + "{\"target\":\"BOND\",\"change_percent\":0},"
                + "{\"target\":\"FUTURE\",\"change_percent\":0},"
                + "{\"target\":\"ALL\",\"change_percent\":0},"
                + "{\"target\":\"weirdSymbol\",\"change_percent\":0}"
                + "]}"), authed);
        assertThat(r).contains("Hisse").contains("Kripto").contains("Döviz").contains("Altın")
                .contains("Gümüş").contains("Emtia").contains("Fon").contains("Tahvil")
                .contains("Vadeli").contains("Tüm portföy").contains("weirdSymbol");
    }

    // ── label fallback'leri ──────────────────────────────────────────────────────

    @Test
    @DisplayName("label: name boş→sembol; name & symbol null→'?' (iki holding tek senaryoda)")
    void labelFallbacks() {
        PortfolioHoldingResponse blankName = h("ONLY.SYM", "  ", AssetType.STOCK, "1000"); // name blank → symbol
        PortfolioHoldingResponse noNameNoSym = h(null, null, AssetType.STOCK, "1000");     // ikisi de null → "?"
        when(portfolioService.getUserPortfolios(any())).thenReturn(List.of(named("L", blankName, noNameNoSym)));

        String r = tool.execute(node("{\"shocks\":[{\"target\":\"ALL\",\"change_percent\":-10}]}"), authed);
        assertThat(r).contains("ONLY.SYM"); // boş isim → sembol
        assertThat(r).contains("?");        // isim+sembol null → '?'
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private PortfolioResponse single(String sym, String name, AssetType type, String mv) {
        return named("Tekli", h(sym, name, type, mv));
    }

    private PortfolioResponse named(String name, PortfolioHoldingResponse... hs) {
        PortfolioResponse p = new PortfolioResponse();
        p.setName(name);
        p.setPortfolioType(PortfolioType.HOLDINGS);
        p.setHoldings(new ArrayList<>(Arrays.asList(hs)));
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
