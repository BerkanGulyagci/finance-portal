package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import com.finance.portal.portfolio.application.viop.valuation.ViopValuationService;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Branch-coverage tamamlayıcısı: {@link FutureHoldingEnricher} için
 * {@link FutureHoldingEnricherTest}'in DEĞMEDİĞİ dallar:
 *   SHORT yön (changePct/pnl/change yön çevirme), viopDirection non-null arm,
 *   averageCost mevcut vs türetilen, qty/totalCost null & qty signum=0,
 *   margin status üç bandı (HEALTHY/WARNING/CRITICAL) + marginRatio null,
 *   currency null → TRY, detail name null → contractName, asOf null → now,
 *   changePct null, grafik: null-value continue / day-null / kısa & bozuk tarih /
 *   tüm value null → erken çıkış / MA50, kanonik==sembol → grafik 2. kez çağrılmaz,
 *   UnsupportedViopContractException.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FutureHoldingEnricherMoreTest {

    @Mock ViopService viopService;
    @Mock ViopChartService viopChartService;
    @Mock ViopContractSpecRegistry specRegistry;
    // USD-kote kontrat FX çevirimi için (TRY fallback spec'te çağrılmaz → bu testlerde stub'sız mock yeter).
    @Mock com.finance.portal.market.application.service.MarketFxService marketFxService;
    @Mock com.finance.portal.market.application.fx.port.TcmbFxHistoryPort tcmbFxHistoryPort;

    // Gerçek valuation service — saf math (mocklamaya değmez).
    private final ViopValuationService valuationService = new ViopValuationService();

    private FutureHoldingEnricher enricher;

    @BeforeEach
    void setUp() {
        // Varsayılan: fallback spec (multiplier=1, marginRate=0.15). Testler gerekirse override eder.
        when(specRegistry.resolveOrFallback(anyString()))
                .thenAnswer(inv -> ViopContractSpec.fallback(inv.getArgument(0)));
        enricher = new FutureHoldingEnricher(viopService, viopChartService, specRegistry, valuationService,
                marketFxService, tcmbFxHistoryPort);
    }

    // ── helpers (Mockito matcher isimlerini GÖLGELEMEMEK için pos/mk/det adları) ──

    private static PortfolioHoldingResponse pos(String symbol, BigDecimal qty, BigDecimal cost) {
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol(symbol);
        h.setTotalQuantity(qty);
        h.setTotalCost(cost);
        return h;
    }

    private static ViopContractDetail det(BigDecimal last, BigDecimal settlement,
                                          BigDecimal prevSettlement, String name) {
        ViopContractDetail d = new ViopContractDetail();
        d.setName(name);
        d.setLastPrice(last);
        d.setSettlementPrice(settlement);
        d.setPrevSettlementPrice(prevSettlement);
        d.setChangePercent(new BigDecimal("0.8"));
        d.setHigh(new BigDecimal("110"));
        d.setLow(new BigDecimal("95"));
        d.setTime("2026-05-26T17:30:00");
        return d;
    }

    /** multiplier ve marginRate'i serbest seçtiren custom spec (currency=TRY, PHYSICAL). */
    private static ViopContractSpec spec(String code, String multiplier, String marginRate, String currency) {
        return new ViopContractSpec(
                code,
                ViopContractSpec.AssetClass.SINGLE_STOCK,
                new BigDecimal(multiplier),
                new BigDecimal(marginRate),
                currency,
                ViopContractSpec.SettlementType.PHYSICAL);
    }

    /** N adet DİSTİNKT takvim günü (ISO yyyy-MM-dd, length=10, parse-edilebilir), artan değerli. */
    private static List<ViopChartPoint> chart(int days) {
        List<ViopChartPoint> pts = new ArrayList<>();
        LocalDate base = LocalDate.of(2025, 1, 1);
        long ts = 1_700_000_000_000L;
        for (int i = 0; i < days; i++) {
            pts.add(new ViopChartPoint(ts, base.plusDays(i).toString(), new BigDecimal(i + 1)));
            ts += 24L * 60 * 60 * 1000;
        }
        return pts;
    }

    // ── USD-kote IDEMPOTENCY: enricher 2 kez çağrılınca averageCost ×fx² OLMAZ (çifte-FX bug fix) ──

    @Test
    @DisplayName("enrich: USD-kote — 2 kez çağrılınca averageCost ×fx² OLMAZ (idempotent, çifte-FX yok)")
    void enrich_usdQuoted_idempotent_noDoubleFx() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        // USD-kote spec (EURUSD): multiplier=1, marginRate=0.04, currency=USD
        when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec("EURUSD", "1", "0.04", "USD"));
        // USD/TRY satış kuru = 40 (deterministik)
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(
                new com.finance.portal.market.application.fx.model.FxLatestRates(null, null, null, "2026-06-01",
                        List.of(new com.finance.portal.market.application.fx.model.FxRateItem(
                                "USD", new BigDecimal("39.9"), new BigDecimal("40"), 0))));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("F_EURUSD0625")).thenReturn(Optional.of(c));
        // güncel=1.20, settle=1.18, prevSettle=1.18 (ham USD)
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("1.20"), new BigDecimal("1.18"), new BigDecimal("1.18"), "F_EURUSD0625"));

        PortfolioHoldingResponse h = pos("F_EURUSD0625", new BigDecimal("1"), new BigDecimal("999"));
        h.setAverageCost(new BigDecimal("1.10"));  // ham USD giriş fiyatı
        h.setViopDirection("LONG");

        // İLK enrich: tüm USD-kote alanlar TL'ye çevrilir.
        enricher.enrich(h);
        // averageCost = 1.10 × 40 = 44.00
        assertThat(h.getAverageCost()).isEqualByComparingTo("44.00");
        // marginPosted = 1 × (1.10×40) × 1 × 0.04 = 1.76
        BigDecimal margin1 = h.getViopMarginPosted();
        assertThat(margin1).isEqualByComparingTo("1.76");
        // pnl = (1.20−1.10) × 40 × 1 × 1 = 4.00
        BigDecimal pnl1 = h.getProfitLoss();
        assertThat(pnl1).isEqualByComparingTo("4.00");

        // İKİNCİ enrich (detay yolundaki double-enrich simülasyonu): AYNI nesne tekrar.
        // BUG olsaydı: averageCost 44×40=1760, marginPosted 44×40×0.04=70.4, pnl (1.20−44)×40=KORKUNÇ.
        // FIX ile: avgEntry ham USD'ye (÷fx) geri döner → HER alan İLK çağrıyla AYNI (idempotent).
        enricher.enrich(h);
        assertThat(h.getAverageCost())
                .as("averageCost ×fx² olmamalı (idempotent)").isEqualByComparingTo("44.00");
        assertThat(h.getViopMarginPosted())
                .as("marginPosted ikinci enrich'te DEĞİŞMEMELİ").isEqualByComparingTo(margin1);
        assertThat(h.getProfitLoss())
                .as("pnl ikinci enrich'te DEĞİŞMEMELİ").isEqualByComparingTo(pnl1);
    }

    // ── MARK-TO-MARKET FX: teminat per-date (giriş kuru), K/Z + gösterim güncel kur + idempotent ──

    @Test
    @DisplayName("enrich: USD-kote mark-to-market — teminat giriş kuru (40), K/Z+gösterim bugün kuru (46); idempotent")
    void enrich_usdQuoted_perDateFx_andIdempotent() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec("EURUSD", "1000", "0.04", "USD"));
        // GÜNCEL kur = 46 (marketFxService anlık)
        when(marketFxService.getTcmbLatestRates("USD")).thenReturn(
                new com.finance.portal.market.application.fx.model.FxLatestRates(null, null, null, "2026-06-20",
                        List.of(new com.finance.portal.market.application.fx.model.FxRateItem(
                                "USD", new BigDecimal("45.9"), new BigDecimal("46"), 0))));
        // ALIŞ GÜNÜ (2026-03-15) kuru = 40 (tcmbFxHistoryPort tarihsel) — SADECE teminata uygulanır.
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any())).thenReturn(List.of(
                new com.finance.portal.market.application.fx.model.FxHistoryPoint("2026-03-15", new BigDecimal("40"))));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("F_EURUSD0625")).thenReturn(Optional.of(c));
        // güncel 1.20 (USD), settle/prev 1.18
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("1.20"), new BigDecimal("1.18"), new BigDecimal("1.18"), "F_EURUSD0625"));

        PortfolioHoldingResponse h = pos("F_EURUSD0625", new BigDecimal("1"), new BigDecimal("999"));
        h.setAverageCost(new BigDecimal("1.10"));  // ham USD giriş
        h.setViopDirection("LONG");
        h.setFirstBuyDate(java.time.LocalDateTime.of(2026, 3, 15, 10, 0));

        enricher.enrich(h);
        // averageCost = 1.10 × fxNow(46) = 50.60  (RESMİ mark-to-market: maliyet güncel kurla TL,
        // tablo iç-tutarlı: K/Z = (mevcut_TL − ortalama_TL) × mult)
        assertThat(h.getAverageCost()).isEqualByComparingTo("50.60");
        // marginPosted = 1 × (1.10×fxEntry40) × 1000 × 0.04 = 1760.00  (TEMİNAT per-date: giriş kuru,
        // resmi örnek de açılış kurundan hesaplar)
        BigDecimal margin1 = h.getViopMarginPosted();
        assertThat(margin1).isEqualByComparingTo("1760.00");
        // pnl = (1.20−1.10) × fxNow(46) × 1000 × +1 = 4600.00  (mark-to-market: saf parite × güncel kur;
        // giriş kuru K/Z'ye GİRMEZ → kur kazancı şişirmesi YOK)
        BigDecimal pnl1 = h.getProfitLoss();
        assertThat(pnl1).isEqualByComparingTo("4600.00");

        // İKİNCİ enrich (double-enrich): averageCost TL(50.60), currency "TRY" → fxNow(46) ile geri-böl, AYNI sonuç.
        enricher.enrich(h);
        assertThat(h.getAverageCost()).as("idempotent — ×fxNow² olmamalı").isEqualByComparingTo("50.60");
        assertThat(h.getViopMarginPosted()).as("marginPosted değişmemeli").isEqualByComparingTo(margin1);
        assertThat(h.getProfitLoss()).as("pnl değişmemeli").isEqualByComparingTo(pnl1);
    }

    @Test
    @DisplayName("enrich: TL-kote per-date kuru GÖRMEZDEN gelir (firstBuyDate olsa bile) — değişmez")
    void enrich_tryQuoted_perDateIgnored() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec("AKBNK", "100", "0.146", "TRY"));
        // Kur stub'lansa bile TRY-kote'de etkisiz olmalı.
        when(tcmbFxHistoryPort.fetchHistory(eq("USD"), any(), any())).thenReturn(List.of(
                new com.finance.portal.market.application.fx.model.FxHistoryPoint("2026-03-15", new BigDecimal("40"))));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("F_AKBNK0625")).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("83"), new BigDecimal("83"), new BigDecimal("82"), "F_AKBNK0625"));

        PortfolioHoldingResponse h = pos("F_AKBNK0625", new BigDecimal("1"), new BigDecimal("8200"));
        h.setAverageCost(new BigDecimal("82"));
        h.setViopDirection("LONG");
        h.setFirstBuyDate(java.time.LocalDateTime.of(2026, 3, 15, 10, 0));

        enricher.enrich(h);
        // TRY-kote: fx UYGULANMAZ. averageCost ham TL 82 kalır, pnl = 100 × (83−82) = 100.
        assertThat(h.getAverageCost()).isEqualByComparingTo("82");
        assertThat(h.getProfitLoss()).isEqualByComparingTo("100.00");
        assertThat(h.getViopMarginPosted()).isEqualByComparingTo("1197.20"); // 1×82×100×0.146
    }

    // ── SHORT yön + viopDirection non-null + averageCost mevcut + custom spec ──

    @Test
    @DisplayName("enrich: SHORT — changePct/pnl/change yön çevrilir, viopDirection 'LONG'a EZİLMEZ")
    void enrich_short_flipsSignsAndKeepsDirection() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        // multiplier=1, marginRate=0.5 → marginPosted kolay hesaplanır
        when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec("AKBNK", "1", "0.5", "TRY"));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("F_AKBNK0625")).thenReturn(Optional.of(c));
        // current=90, prevSettle=92 ; SHORT'ta fiyat düşüşü = kâr
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("90"), new BigDecimal("91"), new BigDecimal("92"), "F_AKBNK0625"));

        PortfolioHoldingResponse h = pos("F_AKBNK0625", new BigDecimal("1"), new BigDecimal("999"));
        h.setAverageCost(new BigDecimal("100"));   // türetme yerine doğrudan kullanılır
        h.setViopDirection("SHORT");

        enricher.enrich(h);

        // dirSign = -1
        // pnl = (90 − 100) × 1 × 1 × (-1) = +10.00
        assertThat(h.getProfitLoss()).isEqualByComparingTo("10.00");
        // marginPosted = 1 × 100 × 1 × 0.5 = 50.00 ; mv = 50 + 10 = 60.00
        assertThat(h.getMarketValue()).isEqualByComparingTo("60.00");
        assertThat(h.getTotalCost()).isEqualByComparingTo("50.00");
        // changePercent = 0.8 × (-1) = -0.8
        assertThat(h.getChangePercent()).isEqualByComparingTo("-0.8");
        // change = (90 − 92) × mult(1) × dirSign(-1) = +2.0000
        assertThat(h.getChange()).isEqualByComparingTo("2.0000");
        // viopDirection null DEĞİL → "LONG" yazılmaz, "SHORT" korunur
        assertThat(h.getViopDirection()).isEqualTo("SHORT");
        // ratio = 60/50 = 1.2000 > 0.50 → HEALTHY
        assertThat(h.getMarginRatio()).isEqualByComparingTo("1.2000");
        assertThat(h.getMarginStatus()).isEqualTo("HEALTHY");
        // viop ek alanları
        assertThat(h.getViopMultiplier()).isEqualByComparingTo("1");
        assertThat(h.getViopMarginRate()).isEqualByComparingTo("0.5");
        assertThat(h.getViopMarginPosted()).isEqualByComparingTo("50.00");
    }

    // ── margin status WARNING bandı (0.25 < ratio ≤ 0.50) ──

    @Test
    @DisplayName("enrich: marginRatio 0.40 → WARNING bandı")
    void enrich_marginWarningBand() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec("AKBNK", "1", "0.5", "TRY"));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        // LONG, avgEntry=100, current=70 → pnl=-30 ; marginPosted=50 ; mv=20 ; ratio=0.40
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("70"), null, null, "X"));

        PortfolioHoldingResponse h = pos("X", new BigDecimal("1"), BigDecimal.ZERO);
        h.setAverageCost(new BigDecimal("100"));
        enricher.enrich(h);

        assertThat(h.getMarginRatio()).isEqualByComparingTo("0.4000");
        assertThat(h.getMarginStatus()).isEqualTo("WARNING");
    }

    // ── margin status CRITICAL bandı (ratio ≤ 0.25) ──

    @Test
    @DisplayName("enrich: marginRatio 0.20 → CRITICAL bandı")
    void enrich_marginCriticalBand() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec("AKBNK", "1", "0.5", "TRY"));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        // avgEntry=100, current=60 → pnl=-40 ; marginPosted=50 ; mv=10 ; ratio=0.20
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("60"), null, null, "X"));

        PortfolioHoldingResponse h = pos("X", new BigDecimal("1"), BigDecimal.ZERO);
        h.setAverageCost(new BigDecimal("100"));
        enricher.enrich(h);

        assertThat(h.getMarginRatio()).isEqualByComparingTo("0.2000");
        assertThat(h.getMarginStatus()).isEqualTo("CRITICAL");
    }

    // ── marginPosted ≤ 0 → marginRatio null & marginStatus null; qty=ZERO avgEntry-türetme false arm ──

    @Test
    @DisplayName("enrich: qty=0 → marginPosted 0 → marginRatio/marginStatus null (avgEntry türetme false arm)")
    void enrich_zeroQty_nullMarginRatio() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("100"), null, null, "X"));

        // qty=0, averageCost null → avgEntry: qty.signum()>0 FALSE → ZERO ; marginPosted=0
        PortfolioHoldingResponse h = pos("X", BigDecimal.ZERO, new BigDecimal("250"));
        enricher.enrich(h);

        assertThat(h.getMarginRatio()).isNull();
        assertThat(h.getMarginStatus()).isNull();
        // marginPosted 0 → totalCost override 0
        assertThat(h.getTotalCost()).isEqualByComparingTo("0");
        assertThat(h.getViopMarginPosted()).isEqualByComparingTo("0");
    }

    // ── qty null & totalCost null → ZERO defaults; averageCost null & qty null → avgEntry ZERO ──

    @Test
    @DisplayName("enrich: totalQuantity & totalCost & averageCost null → ZERO default'ları, NPE yok")
    void enrich_nullQuantityCostAverage_defaultsZero() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("100"), null, null, "X"));

        // qty=null → ZERO ; cost=null → ZERO ; averageCost=null → qty.signum()>0 false → ZERO
        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setSymbol("X");
        enricher.enrich(h);

        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100");
        assertThat(h.getMarketValue()).isEqualByComparingTo("0");   // margin 0 + pnl 0
        assertThat(h.getProfitLoss()).isEqualByComparingTo("0");
        assertThat(h.getViopNotional()).isEqualByComparingTo("0");
        assertThat(h.getMarginRatio()).isNull();
        // viopDirection null'dı → "LONG" backward-compat yazıldı
        assertThat(h.getViopDirection()).isEqualTo("LONG");
    }

    // ── currency null → "TRY" fallback ; detail name null → contractName ; asOf null → now ──

    @Test
    @DisplayName("enrich: spec.currency null → TRY ; detail.name null → contractName ; time bozuk → asOf now")
    void enrich_currencyNull_nameNull_asOfNow() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());
        // currency=null → "TRY" fallback dalı
        when(specRegistry.resolveOrFallback(anyString())).thenReturn(spec("X", "1", "0.15", null));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        ViopContractDetail d = det(new BigDecimal("100"), null, null, null); // name null
        d.setTime("not-a-timestamp");                                        // parseLenient → null → now
        when(viopService.buildDetailDto(c)).thenReturn(d);

        PortfolioHoldingResponse h = pos("MYSYMBOL", new BigDecimal("1"), new BigDecimal("100"));
        enricher.enrich(h);

        assertThat(h.getCurrency()).isEqualTo("TRY");      // spec currency null → fallback
        assertThat(h.getName()).isEqualTo("MYSYMBOL");     // detail name null → contractName
        assertThat(h.getAsOf()).isNotNull();               // bozuk time → now() ile dolduruldu
    }

    // ── changePercent null → setChangePercent çağrılmaz ──

    @Test
    @DisplayName("enrich: detail.changePercent null → changePercent set edilmez")
    void enrich_changePercentNull_notSet() {
        when(viopChartService.getChart(any(), any())).thenReturn(List.of());

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        ViopContractDetail d = det(new BigDecimal("100"), null, null, "X");
        d.setChangePercent(null);
        when(viopService.buildDetailDto(c)).thenReturn(d);

        PortfolioHoldingResponse h = pos("X", new BigDecimal("1"), new BigDecimal("100"));
        enricher.enrich(h);

        assertThat(h.getChangePercent()).isNull();
    }

    // ── kanonik isim == sembol (trim sonrası) → grafik İKİNCİ kez çağrılmaz ──

    @Test
    @DisplayName("enrich: detail.name == symbol → grafik ikinci kez (kanonik) ÇAĞRILMAZ")
    void enrich_canonicalEqualsSymbol_chartCalledOnce() {
        when(viopChartService.getChart(eq("SAME"), eq(ViopChartPeriod.ONE_YEAR))).thenReturn(chart(5));
        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("SAME")).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("100"), null, null, "SAME")); // canonical == symbol

        PortfolioHoldingResponse h = pos("SAME", new BigDecimal("1"), new BigDecimal("100"));
        enricher.enrich(h);

        // canonical.trim().equals(contractName) TRUE → ikinci grafik denemesi yok → toplam 1Y çağrısı
        verify(viopChartService, times(1)).getChart("SAME", ViopChartPeriod.ONE_YEAR);
    }

    // ── grafik: null-value continue + day-null (null/kısa/bozuk tarih) — 52w yine dolar, MA50 dahil ──

    @Test
    @DisplayName("applyViopYearChartMetrics: null-value & null/kısa/bozuk-tarih noktaları atlanır, 52w + MA50 dolar")
    void chart_nullValueAndBadDatePoints_stillFill52wAndMa50() {
        List<ViopChartPoint> pts = new ArrayList<>(chart(55)); // 55 distinct gün → MA50 dolar
        // value=null → 'continue' dalı (allVals'a EKLENMEZ)
        pts.add(new ViopChartPoint(9_999_999_999_999L, "2025-12-31", null));
        // dateTime=null → chartPointToLocalDate null (day-null dalı) ama value var → allVals'a girer
        pts.add(new ViopChartPoint(9_999_999_999_998L, null, new BigDecimal("500")));
        // dateTime kısa (<10) → chartPointToLocalDate null
        pts.add(new ViopChartPoint(9_999_999_999_997L, "2025", new BigDecimal("501")));
        // dateTime 10-char ama parse-edilemez → catch → null
        pts.add(new ViopChartPoint(9_999_999_999_996L, "20XX-13-99", new BigDecimal("502")));
        // timestamp=null → sıralama Comparator.nullsLast dalı
        pts.add(new ViopChartPoint(null, "2025-01-01", new BigDecimal("7")));

        when(viopChartService.getChart(eq("BAD"), eq(ViopChartPeriod.ONE_YEAR))).thenReturn(pts);
        when(viopService.findMatchingContract("BAD")).thenReturn(Optional.empty());

        PortfolioHoldingResponse h = pos("BAD", new BigDecimal("1"), BigDecimal.ZERO);
        enricher.enrich(h);

        // value=502 en yüksek (bozuk-tarihli noktanın value'su allVals'a girdi)
        assertThat(h.getFiftyTwoWeekHigh()).isNotNull();
        assertThat(h.getFiftyTwoWeekHigh()).isEqualByComparingTo("502");
        assertThat(h.getFiftyTwoWeekLow()).isNotNull();
        // 50+ distinct günlük kapanış → MA50 hesaplanabilir
        assertThat(h.getMa50()).isNotNull();
        assertThat(h.getMa20()).isNotNull();
    }

    // ── grafik: TÜM noktaların value'su null (size≥2) → allVals boş → erken çıkış, 52w/MA yok ──

    @Test
    @DisplayName("applyViopYearChartMetrics: tüm value'lar null → allVals boş → 52w/MA dolmaz")
    void chart_allValuesNull_noMetrics() {
        List<ViopChartPoint> pts = new ArrayList<>();
        pts.add(new ViopChartPoint(1L, "2025-01-01", null));
        pts.add(new ViopChartPoint(2L, "2025-01-02", null));
        pts.add(new ViopChartPoint(3L, "2025-01-03", null));
        when(viopChartService.getChart(eq("NULLS"), eq(ViopChartPeriod.ONE_YEAR))).thenReturn(pts);
        when(viopService.findMatchingContract("NULLS")).thenReturn(Optional.empty());

        PortfolioHoldingResponse h = pos("NULLS", new BigDecimal("1"), BigDecimal.ZERO);
        enricher.enrich(h);

        assertThat(h.getFiftyTwoWeekHigh()).isNull();
        assertThat(h.getFiftyTwoWeekLow()).isNull();
        assertThat(h.getMa20()).isNull();
        assertThat(h.getMa50()).isNull();
    }

    // ── grafik: UnsupportedViopContractException → erken çıkış (52w dolmaz, ama enrich devam) ──

    @Test
    @DisplayName("applyViopYearChartMetrics: UnsupportedViopContractException → grafik atlanır, liste yolu devam")
    void chart_unsupportedException_skippedButListContinues() {
        when(viopChartService.getChart(any(), any()))
                .thenThrow(new UnsupportedViopContractException("endeks grafiği desteklenmiyor"));

        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract(any())).thenReturn(Optional.of(c));
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("100"), null, null, "X"));

        PortfolioHoldingResponse h = pos("X", new BigDecimal("1"), new BigDecimal("100"));
        enricher.enrich(h);

        // grafik exception → 52w yok ama liste yolu fiyatı doldurdu
        assertThat(h.getFiftyTwoWeekHigh()).isNull();
        assertThat(h.getCurrentPrice()).isEqualByComparingTo("100");
    }

    // ── grafik chartContractName blank → applyViopYearChartMetrics erken çıkar (ikinci çağrı yolu) ──

    @Test
    @DisplayName("applyViopYearChartMetrics: kanonik isim boş/whitespace → grafik getChart çağrılmaz (blank guard)")
    void chart_blankCanonical_guard() {
        when(viopChartService.getChart(eq("REAL"), any())).thenReturn(chart(5));
        ViopContract c = new ViopContract();
        when(viopService.findMatchingContract("REAL")).thenReturn(Optional.of(c));
        // canonical name = "   " (blank) → trim sonrası "" != "REAL" → ikinci kez denenir ama blank-guard erken çıkar
        when(viopService.buildDetailDto(c))
                .thenReturn(det(new BigDecimal("100"), null, null, "   "));

        PortfolioHoldingResponse h = pos("REAL", new BigDecimal("1"), new BigDecimal("100"));
        enricher.enrich(h);

        // İlk grafik "REAL" ile çağrıldı; blank kanonik için getChart("   ", ...) çağrılmamalı
        verify(viopChartService, times(1)).getChart("REAL", ViopChartPeriod.ONE_YEAR);
        verify(viopChartService, never()).getChart(eq("   "), any());
        // name: detail.name "   " (non-null) → o set edilir
        assertThat(h.getName()).isEqualTo("   ");
    }
}
