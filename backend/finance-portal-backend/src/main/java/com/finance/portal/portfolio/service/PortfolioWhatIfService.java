package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import com.finance.portal.portfolio.application.whatif.PortfolioWhatIfResult;
import com.finance.portal.portfolio.application.whatif.WhatIfScenario;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesPoint;
import com.finance.portal.portfolio.application.whatif.WhatIfSeriesResult;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Portföy geneli "Ne Olurdu?" hesaplayıcı: her pozisyonun TL maliyetini, ilk alış tarihinden
 * bugüne alternatif araçlarda (enflasyon/altın/dolar/mevduat) büyütüp toplar; gerçek portföy
 * değerine karşı kıyaslar. Fırsat maliyeti görünümü.
 *
 * <p>Maliyet ve tarih, reel getiri ile aynı basitleştirmeden gelir (pozisyonun {@code totalCost}'u
 * ve {@code firstBuyDate}'i; lot ağırlıklı değil). Faktör = bugünkü fiyat / alış tarihindeki fiyat;
 * enflasyonda TÜFE endeksi. Bir senaryonun verisi yoksa o pozisyon için faktör 1 (nakit gibi durur),
 * tüm kaynak yoksa senaryo {@code available=false}.
 */
@Service
public class PortfolioWhatIfService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioWhatIfService.class);

    private static final int MONEY_SCALE = 2;
    private static final int PCT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final ZoneId TR_ZONE = ZoneId.of("Europe/Istanbul");
    /** EVDS TL mevduat faizi (haftalık, yıllık %). */
    private static final String DEPOSIT_RATE_CODE = "TP.TRY.MT02";
    /** Kullanıcı en fazla bu kadar serbest kıyas ekleyebilir (grafik taşmasın). */
    private static final int MAX_BENCHMARKS = 3;

    private final InflationDeflatorService deflator;
    private final PortfolioHistoricalPricePort pricePort;
    private final EconomyDataPort economyDataPort;
    private final PortfolioCurrencyConverter currencyConverter;

    public PortfolioWhatIfService(InflationDeflatorService deflator,
                                  PortfolioHistoricalPricePort pricePort,
                                  EconomyDataPort economyDataPort,
                                  PortfolioCurrencyConverter currencyConverter) {
        this.deflator = deflator;
        this.pricePort = pricePort;
        this.economyDataPort = economyDataPort;
        this.currencyConverter = currencyConverter;
    }

    /** Bir pozisyonun kıyas verisi: TL maliyet, bugünkü TL değer, ilk alış tarihi. */
    private record Pos(BigDecimal costTl, BigDecimal mvTl, LocalDate date) {}

    private record DateRate(LocalDate date, double rate) {}

    @WithSpan("PortfolioWhatIf.compute")
    public PortfolioWhatIfResult compute(PortfolioResponse resp) {
        PortfolioWhatIfResult result = new PortfolioWhatIfResult();
        LocalDate today = LocalDate.now(TR_ZONE);
        result.setAsOf(today);
        result.setPortfolioId(resp != null ? resp.getId() : null);

        List<PortfolioHoldingResponse> holdings = (resp != null && resp.getHoldings() != null)
                ? resp.getHoldings() : List.of();

        List<Pos> positions = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal actualValue = BigDecimal.ZERO;
        int skipped = 0;
        LocalDate earliest = today;

        for (PortfolioHoldingResponse h : holdings) {
            BigDecimal costTl = currencyConverter.toTry(h.getTotalCost(), h.getCurrency());
            BigDecimal mvTl = currencyConverter.toTry(h.getMarketValue(), h.getCurrency());
            LocalDate date = h.getFirstBuyDate() != null ? h.getFirstBuyDate().toLocalDate() : null;
            if (costTl == null || costTl.signum() <= 0 || mvTl == null || date == null) {
                skipped++;
                continue;
            }
            // "Gerçek" = pozisyonun gerçek piyasa değeri (mvTl = toTry(holding.marketValue)). BOND'larda
            // da cost ve MV artık tutarlı TL (eurobond cost FX çevirimi kaldırıldı) → ekstra ratio
            // düzeltmesine gerek yok; actualReturn = mvTl/costTl doğrudan Holdings getirisidir.
            // Çoklu BUY'da her lot kendi alış tarihinden senaryo faktörüyle çarpılır.
            // Tek lot ya da lot bilgisi yoksa eski tek-Pos davranışı korunur.
            List<PortfolioHoldingResponse.CostLot> lots = h.getOpenCostLots();
            if (lots == null || lots.isEmpty()) {
                positions.add(new Pos(costTl, mvTl, date));
            } else {
                // MV proportional bölüştürme: lot.costTl × totalMv / totalCost
                BigDecimal totalLotCostNative = BigDecimal.ZERO;
                for (PortfolioHoldingResponse.CostLot lot : lots) {
                    if (lot.cost() != null && lot.cost().signum() > 0) {
                        totalLotCostNative = totalLotCostNative.add(lot.cost());
                    }
                }
                if (totalLotCostNative.signum() <= 0) {
                    positions.add(new Pos(costTl, mvTl, date));
                } else {
                    for (PortfolioHoldingResponse.CostLot lot : lots) {
                        if (lot.cost() == null || lot.cost().signum() <= 0 || lot.buyDate() == null) continue;
                        BigDecimal lotCostTl = currencyConverter.toTry(lot.cost(), h.getCurrency());
                        if (lotCostTl == null || lotCostTl.signum() <= 0) continue;
                        BigDecimal lotMvTl = mvTl.multiply(lotCostTl)
                                .divide(costTl, MathContext.DECIMAL64);
                        positions.add(new Pos(lotCostTl, lotMvTl, lot.buyDate()));
                        if (lot.buyDate().isBefore(earliest)) {
                            earliest = lot.buyDate();
                        }
                    }
                }
            }
            totalCost = totalCost.add(costTl);
            actualValue = actualValue.add(mvTl);
            if (date.isBefore(earliest)) {
                earliest = date;
            }
        }

        result.setTotalCost(totalCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        result.setActualValue(actualValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        result.setIncludedHoldings(positions.size());
        result.setSkippedHoldings(skipped);
        Span.current().setAttribute("whatif.included_holdings", positions.size());
        Span.current().setAttribute("whatif.skipped_holdings", skipped);
        if (totalCost.signum() > 0) {
            result.setActualReturnPercent(pct(actualValue, totalCost));
        }

        List<WhatIfScenario> scenarios = new ArrayList<>();
        result.setScenarios(scenarios);
        if (positions.isEmpty() || totalCost.signum() <= 0) {
            return result;
        }

        final BigDecimal costBase = totalCost;
        final LocalDate from = earliest.minusDays(14);

        // 1) Enflasyon (TÜFE) — fiyat yerine endeks oranı.
        scenarios.add(inflationScenario(positions, costBase, today));
        // 2) Altın (gram) — tarihsel gram altın TL kapanışı.
        scenarios.add(priceScenario("gold", "Altın (gram)", AssetType.GOLD, "GRAM", positions, costBase, from, today));
        // 3) Dolar (USD/TRY) — tarihsel TCMB kuru.
        scenarios.add(priceScenario("usd", "Dolar (USD)", AssetType.FX, "USD", positions, costBase, from, today));
        // 4) Mevduat (brüt) — EVDS haftalık TL mevduat faizi, bileşik.
        scenarios.add(depositScenario(positions, costBase, from, today));

        return result;
    }

    /**
     * Özel/simülasyon modu: "istediğim tarihte istediğim varlığı X TL ile alsaydım ne olurdu".
     * Sentetik tek pozisyon kurup {@link #computeSeries} motorunu yeniden kullanır.
     */
    @WithSpan("PortfolioWhatIf.computeSimSeries")
    public WhatIfSeriesResult computeSimSeries(@SpanAttribute("whatif.asset_type") String assetType,
                                               @SpanAttribute("whatif.symbol") String symbol, BigDecimal amountTl,
                                               LocalDate date, List<String> benchmarks) {
        WhatIfSeriesResult empty = new WhatIfSeriesResult();
        empty.setAsOf(LocalDate.now(TR_ZONE));
        empty.setScope("SIM");
        empty.setPoints(new ArrayList<>());
        empty.setAvailableScenarios(new ArrayList<>());
        if (assetType == null || symbol == null || symbol.isBlank()
                || amountTl == null || amountTl.signum() <= 0 || date == null) {
            return empty;
        }
        AssetType type;
        try {
            type = AssetType.valueOf(assetType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return empty;
        }
        if (date.isAfter(LocalDate.now(TR_ZONE))) {
            return empty;
        }

        PortfolioHoldingResponse h = new PortfolioHoldingResponse();
        h.setAssetType(type);
        h.setSymbol(symbol.trim());
        h.setTotalCost(amountTl);
        h.setMarketValue(amountTl);
        h.setCurrency("TRY");
        h.setFirstBuyDate(date.atStartOfDay());

        PortfolioResponse resp = new PortfolioResponse();
        resp.setHoldings(List.of(h));

        WhatIfSeriesResult r = computeSeries(resp, type.name(), h.getSymbol(), benchmarks);
        r.setScope("SIM");
        r.setLabel(h.getSymbol());
        return r;
    }

    // ── Zaman serisi ("Ne Olurdu?" çizgi grafiği) ──────────────────────────────

    /** Tek bir pozisyonun seri hesabı için veri taşıyıcısı. */
    private static final class SeriesPos {
        final BigDecimal costTl;
        final LocalDate date;
        final NavigableMap<LocalDate, BigDecimal> assetSeries;
        /**
         * Bu lot'un fiyat-ölçeğine göre efektif adedi (= qty / parScale; bond /100, diğer 1).
         * "Gerçek" çizgisi = Σ effQty × seriFiyatı[t] = pozisyonun gerçek piyasa değeri (alış-tarihi
         * seri fiyatına bağlı oran DEĞİL → gerçek alış fiyatından sapmaz). Null ise (eski/qty'siz
         * veri) çağıran eski ratio davranışına düşer.
         */
        BigDecimal effQty;
        BigDecimal assetBase;
        BigDecimal goldBase;
        BigDecimal usdBase;
        BigDecimal cpiBase;
        BigDecimal usCpiBase;
        BigDecimal bistBase;
        BigDecimal btcBase;
        BigDecimal gspcBase;
        final Map<String, BigDecimal> benchBase = new HashMap<>();

        SeriesPos(BigDecimal costTl, LocalDate date, NavigableMap<LocalDate, BigDecimal> assetSeries) {
            this.costTl = costTl;
            this.date = date;
            this.assetSeries = assetSeries;
        }
    }

    /** Pozisyon listesi + en eski tarih + (single mod) tek varlık etiketi — buildPositions çıktısı. */
    private static final class PositionBundle {
        final List<SeriesPos> positions;
        final LocalDate earliest;
        final String singleLabel;
        PositionBundle(List<SeriesPos> positions, LocalDate earliest, String singleLabel) {
            this.positions = positions;
            this.earliest = earliest;
            this.singleLabel = singleLabel;
        }
    }

    /**
     * Holdings'i geçerli pozisyonlara (TL maliyet + alış tarihi + asset serisi) çevirir.
     * Eksik veri (costTl ≤ 0 ya da date null) olan satırları eler. computeSeries'in cognitive
     * complexity'sini düşürmek için ayrıldı; tek başına 7 dallı bir döngü.
     *
     * <p>S3776 suppress: her dal (filter eşleşmesi, cost/date validasyonu, price-fetch try/catch,
     * earliest takibi, single-mod etiket) ayrı bir invariantı koruyor; daha alt helper'lara
     * bölmek paramlar/return tuple'ları nedeniyle okunabilirliği bozar.
     */
    @SuppressWarnings("java:S3776")
    private PositionBundle buildPositions(List<PortfolioHoldingResponse> holdings, LocalDate today,
                                          String filterAssetType, String filterSymbol, boolean single) {
        List<SeriesPos> positions = new ArrayList<>();
        LocalDate earliest = today;
        String singleLabel = null;
        for (PortfolioHoldingResponse h : holdings) {
            if (single && !matches(h, filterAssetType, filterSymbol)) {
                continue;
            }
            BigDecimal costTl = currencyConverter.toTry(h.getTotalCost(), h.getCurrency());
            LocalDate date = h.getFirstBuyDate() != null ? h.getFirstBuyDate().toLocalDate() : null;
            if (costTl == null || costTl.signum() <= 0 || date == null) {
                continue;
            }
            // Asset series tüm holding için bir kez çekilir — lots aynı seriyi paylaşır.
            // Earliest lot tarihi 10 gün geriden çekim penceresi başlangıcı olur.
            LocalDate fetchFrom = date;
            List<PortfolioHoldingResponse.CostLot> lots = h.getOpenCostLots();
            if (lots != null && !lots.isEmpty()) {
                for (PortfolioHoldingResponse.CostLot lot : lots) {
                    if (lot.buyDate() != null && lot.buyDate().isBefore(fetchFrom)) {
                        fetchFrom = lot.buyDate();
                    }
                }
            }
            NavigableMap<LocalDate, BigDecimal> assetSeries;
            try {
                assetSeries = pricePort.fetchDailyClosePrices(
                        h.getAssetType(), h.getSymbol(), fetchFrom.minusDays(10), today).orElse(null);
            } catch (Exception e) {
                assetSeries = null;
            }
            // Çoklu BUY → her lot ayrı SeriesPos (kendi alış tarihinden). Tek lot ya da lot bilgisi
            // yoksa eski tek-pos davranışı. effQty (= qty/parScale) "Gerçek"in gerçek MV'sini verir.
            BigDecimal parScale = bondParScale(h);
            if (lots == null || lots.isEmpty()) {
                SeriesPos sp = new SeriesPos(costTl, date, assetSeries);
                sp.effQty = effQtyOf(h.getTotalQuantity(), parScale);
                positions.add(sp);
            } else {
                for (PortfolioHoldingResponse.CostLot lot : lots) {
                    if (lot.cost() == null || lot.cost().signum() <= 0 || lot.buyDate() == null) continue;
                    BigDecimal lotCostTl = currencyConverter.toTry(lot.cost(), h.getCurrency());
                    if (lotCostTl == null || lotCostTl.signum() <= 0) continue;
                    SeriesPos sp = new SeriesPos(lotCostTl, lot.buyDate(), assetSeries);
                    sp.effQty = effQtyOf(lot.qty(), parScale);
                    positions.add(sp);
                    if (lot.buyDate().isBefore(earliest)) {
                        earliest = lot.buyDate();
                    }
                }
            }
            if (date.isBefore(earliest)) {
                earliest = date;
            }
            if (single) {
                singleLabel = (h.getName() != null && !h.getName().isBlank()) ? h.getName() : h.getSymbol();
            }
        }
        return new PositionBundle(positions, earliest, singleLabel);
    }

    /** BOND (altın bond hariç) fiyatı 100 nominal üzerinden kote → parScale 100; diğer türler 1. */
    private static BigDecimal bondParScale(PortfolioHoldingResponse h) {
        if (h.getAssetType() != AssetType.BOND) {
            return BigDecimal.ONE;
        }
        String cat = h.getCategory() != null ? h.getCategory() : "";
        boolean goldBond = "GOLD_INDEXED_BOND".equals(cat) || "GOLD_INDEXED_LEASE_CERTIFICATE".equals(cat);
        return goldBond ? BigDecimal.ONE : HUNDRED;
    }

    /** Efektif adet = qty / parScale; qty bilinmiyorsa null (çağıran eski ratio davranışına düşer). */
    private static BigDecimal effQtyOf(BigDecimal qty, BigDecimal parScale) {
        if (qty == null || qty.signum() <= 0) {
            return null;
        }
        return qty.divide(parScale, MathContext.DECIMAL64);
    }

    /** Kullanıcı-eklemeli serbest kıyas tanımı. needsFx: USD cinsi enstrüman (×USD/TRY ile TL'ye çevrilir). */
    private record BenchSpec(String id, AssetType type, String symbol, boolean needsFx) {}

    /** "ASSETTYPE|SYMBOL" listesini ayrıştır — tüm türler; max {@link #MAX_BENCHMARKS}. */
    private static List<BenchSpec> parseBenchmarks(List<String> specs) {
        List<BenchSpec> out = new ArrayList<>();
        if (specs == null) {
            return out;
        }
        Set<String> seen = new HashSet<>();
        for (String s : specs) {
            if (s == null || !s.contains("|")) {
                continue;
            }
            String[] parts = s.split("\\|", 2);
            String typeStr = parts[0].trim().toUpperCase(Locale.ROOT);
            String sym = parts[1].trim();
            if (sym.isEmpty()) {
                continue;
            }
            AssetType type;
            try {
                type = AssetType.valueOf(typeStr);
            } catch (Exception e) {
                continue;
            }
            // Çoğu tür adaptörde TL döner; USD cinsi olanlar (BIST dışı hisse, Yahoo vadeli) ×USD/TRY ile çevrilir.
            String up = sym.toUpperCase(Locale.ROOT);
            boolean needsFx = (type == AssetType.STOCK && !up.endsWith(".IS"))
                    || (type == AssetType.FUTURE && up.contains("=F"));
            String id = type.name() + "|" + sym;
            if (seen.add(id)) {
                out.add(new BenchSpec(id, type, sym, needsFx));
            }
            if (out.size() >= MAX_BENCHMARKS) {
                break;
            }
        }
        return out;
    }

    /**
     * "Ne Olurdu?" zaman serisi: seçilen varlık (ya da tüm portföy) için, yatırılan paranın
     * zaman içinde gerçek/enflasyon/altın/dolar/mevduat değerini aylık noktalarla verir.
     * Her seri = Σ pozisyon maliyeti × (faktör(t) / faktör(alış)). filterAssetType+filterSymbol
     * boşsa tüm portföy.
     *
     * <p>S3776 suppress: yöntem 10 paralel hipotetik seri (gerçek / TÜFE / US-CPI / altın /
     * USD / mevduat / BIST / BTC / S&amp;P500 / benchmarks) için aynı zaman üzerinde toplama
     * yapar. Her seri kendi availability/base/ratio dalına sahip — her birini ayrı helper'a
     * almak 30+ parametre veya çok satırlı tuple/record geçirmek demek; mevcut yapı top-down
     * okunur.
     */
    @SuppressWarnings("java:S3776")
    @WithSpan("PortfolioWhatIf.computeSeries")
    public WhatIfSeriesResult computeSeries(PortfolioResponse resp,
                                            @SpanAttribute("whatif.asset_type") String filterAssetType,
                                            @SpanAttribute("whatif.symbol") String filterSymbol,
                                            List<String> benchmarkSpecs) {
        WhatIfSeriesResult result = new WhatIfSeriesResult();
        LocalDate today = LocalDate.now(TR_ZONE);
        result.setAsOf(today);
        result.setPortfolioId(resp != null ? resp.getId() : null);

        boolean single = filterAssetType != null && !filterAssetType.isBlank()
                && filterSymbol != null && !filterSymbol.isBlank();
        result.setScope(single ? (filterAssetType + ":" + filterSymbol) : "ALL");

        List<PortfolioHoldingResponse> holdings = (resp != null && resp.getHoldings() != null)
                ? resp.getHoldings() : List.of();

        PositionBundle bundle = buildPositions(holdings, today, filterAssetType, filterSymbol, single);
        List<SeriesPos> positions = bundle.positions;
        LocalDate earliest = bundle.earliest != null ? bundle.earliest : today;
        if (single && bundle.singleLabel != null) {
            result.setAssetType(filterAssetType);
            result.setSymbol(filterSymbol);
        }
        result.setLabel(single ? bundle.singleLabel : "Tüm Portföy");
        result.setPoints(new ArrayList<>());
        result.setAvailableScenarios(new ArrayList<>());
        Span.current().setAttribute("whatif.scope", String.valueOf(result.getScope()));
        Span.current().setAttribute("whatif.positions", positions.size());
        if (positions.isEmpty()) {
            return result;
        }

        LocalDate from = earliest.minusDays(14);
        final List<EconomySeriesPoint> tufe = safe(() -> deflator.tufeSeries());
        final List<EconomySeriesPoint> usCpi = safe(() -> deflator.usCpiSeries());
        final NavigableMap<LocalDate, BigDecimal> goldMap = safePrice(AssetType.GOLD, "GRAM", from, today);
        final NavigableMap<LocalDate, BigDecimal> usdMap = safePrice(AssetType.FX, "USD", from, today);
        final List<DateRate> rates = depositRates(from, today);
        // Portföy dışı benchmark'lar — BIST100/Bitcoin TL; S&P500 USD (×USD/TRY ile TL'ye çevrilir).
        final NavigableMap<LocalDate, BigDecimal> bistMap = safePrice(AssetType.STOCK, "XU100.IS", from, today);
        final NavigableMap<LocalDate, BigDecimal> btcMap = safePrice(AssetType.CRYPTO, "BTC", from, today);
        final NavigableMap<LocalDate, BigDecimal> gspcMap = safePrice(AssetType.STOCK, "^GSPC", from, today);

        // Kullanıcı-eklemeli serbest kıyaslar (tüm türler; USD olanlar ×USD/TRY ile TL'ye çevrilir).
        final Map<String, NavigableMap<LocalDate, BigDecimal>> benchMaps = new LinkedHashMap<>();
        final Map<String, Boolean> benchNeedsFx = new HashMap<>();
        for (BenchSpec b : parseBenchmarks(benchmarkSpecs)) {
            NavigableMap<LocalDate, BigDecimal> m = safePrice(b.type(), b.symbol(), from, today);
            if (m != null && !m.isEmpty()) {
                benchMaps.put(b.id(), m);
                benchNeedsFx.put(b.id(), b.needsFx());
            }
        }

        boolean inflAvail = tufe != null && !tufe.isEmpty();
        boolean goldAvail = goldMap != null && !goldMap.isEmpty();
        boolean usdAvail = usdMap != null && !usdMap.isEmpty();
        boolean usCpiAvail = usCpi != null && !usCpi.isEmpty() && usdAvail; // TL'ye çevirmek için kur şart
        boolean depAvail = !rates.isEmpty();
        boolean bistAvail = bistMap != null && !bistMap.isEmpty();
        boolean btcAvail = btcMap != null && !btcMap.isEmpty();
        boolean sp500Avail = gspcMap != null && !gspcMap.isEmpty() && usdAvail;
        boolean actualAvail = false;

        // Pozisyon başına baz değerler (alış tarihindeki seviyeler).
        for (SeriesPos p : positions) {
            p.assetBase = floorVal(p.assetSeries, p.date);
            if (p.assetBase != null && p.assetBase.signum() > 0) {
                actualAvail = true;
            }
            p.goldBase = goldAvail ? floorVal(goldMap, p.date) : null;
            p.usdBase = usdAvail ? floorVal(usdMap, p.date) : null;
            p.cpiBase = inflAvail ? deflator.indexValueAt(tufe, p.date).orElse(null) : null;
            p.usCpiBase = usCpiAvail ? deflator.indexValueAt(usCpi, p.date).orElse(null) : null;
            p.bistBase = bistAvail ? floorVal(bistMap, p.date) : null;
            p.btcBase = btcAvail ? floorVal(btcMap, p.date) : null;
            p.gspcBase = sp500Avail ? floorVal(gspcMap, p.date) : null;
            for (var e : benchMaps.entrySet()) {
                p.benchBase.put(e.getKey(), floorVal(e.getValue(), p.date));
            }
        }

        List<WhatIfSeriesPoint> points = new ArrayList<>();
        for (LocalDate t : monthlyDates(earliest, today)) {
            WhatIfSeriesPoint pt = new WhatIfSeriesPoint(t);
            // t anındaki paylaşılan seviyeler (tüm pozisyonlar için aynı).
            BigDecimal goldT = goldAvail ? floorVal(goldMap, t) : null;
            BigDecimal usdT = (usdAvail || sp500Avail) ? floorVal(usdMap, t) : null;
            BigDecimal bistT = bistAvail ? floorVal(bistMap, t) : null;
            BigDecimal btcT = btcAvail ? floorVal(btcMap, t) : null;
            BigDecimal gspcT = sp500Avail ? floorVal(gspcMap, t) : null;
            BigDecimal cpiT = inflAvail ? deflator.indexValueAt(tufe, t).orElse(null) : null;
            BigDecimal usCpiT = usCpiAvail ? deflator.indexValueAt(usCpi, t).orElse(null) : null;
            Map<String, BigDecimal> benchT = new HashMap<>();
            for (var e : benchMaps.entrySet()) {
                benchT.put(e.getKey(), floorVal(e.getValue(), t));
            }
            Map<String, BigDecimal> extra = new HashMap<>();

            BigDecimal cost = BigDecimal.ZERO, actual = BigDecimal.ZERO, infl = BigDecimal.ZERO,
                    usInfl = BigDecimal.ZERO, gold = BigDecimal.ZERO, usd = BigDecimal.ZERO,
                    dep = BigDecimal.ZERO, bist = BigDecimal.ZERO, btc = BigDecimal.ZERO, sp = BigDecimal.ZERO;
            boolean anyA = false, anyI = false, anyUI = false, anyG = false, anyU = false,
                    anyD = false, anyB = false, anyC = false, anyS = false;

            for (SeriesPos p : positions) {
                if (p.date.isAfter(t)) {
                    continue;
                }
                cost = cost.add(p.costTl);

                if (actualAvail && positive(p.assetBase)) {
                    BigDecimal v = floorVal(p.assetSeries, t);
                    if (positive(v)) {
                        // "Gerçek" = pozisyonun gerçek piyasa değeri. effQty (qty/parScale) varsa
                        // qty × fiyat[t] (gerçek alış fiyatından bağımsız → Holdings ile tutarlı);
                        // yoksa (qty'siz/eski veri) eski seri-oranı fallback'i.
                        actual = actual.add(p.effQty != null
                                ? v.multiply(p.effQty)
                                : ratio(p.costTl, v, p.assetBase));
                        anyA = true;
                    }
                }
                if (goldAvail && positive(goldT) && positive(p.goldBase)) {
                    gold = gold.add(ratio(p.costTl, goldT, p.goldBase)); anyG = true;
                }
                if (usdAvail && positive(usdT) && positive(p.usdBase)) {
                    usd = usd.add(ratio(p.costTl, usdT, p.usdBase)); anyU = true;
                }
                if (inflAvail && positive(cpiT) && positive(p.cpiBase)) {
                    infl = infl.add(ratio(p.costTl, cpiT, p.cpiBase)); anyI = true;
                }
                if (usCpiAvail && positive(usCpiT) && positive(p.usCpiBase) && positive(usdT) && positive(p.usdBase)) {
                    // ABD enflasyonu (TL): (ABD CPI oranı) × (USD/TRY oranı)
                    BigDecimal idx = usCpiT.divide(p.usCpiBase, MathContext.DECIMAL64);
                    BigDecimal fx = usdT.divide(p.usdBase, MathContext.DECIMAL64);
                    usInfl = usInfl.add(p.costTl.multiply(idx).multiply(fx)); anyUI = true;
                }
                if (depAvail) {
                    dep = dep.add(p.costTl.multiply(depositFactor(rates, p.date, t))); anyD = true;
                }
                if (bistAvail && positive(bistT) && positive(p.bistBase)) {
                    bist = bist.add(ratio(p.costTl, bistT, p.bistBase)); anyB = true;
                }
                if (btcAvail && positive(btcT) && positive(p.btcBase)) {
                    btc = btc.add(ratio(p.costTl, btcT, p.btcBase)); anyC = true;
                }
                if (sp500Avail && positive(gspcT) && positive(p.gspcBase) && positive(usdT) && positive(p.usdBase)) {
                    // S&P500 TL faktörü = (endeks oranı) × (USD/TRY oranı)
                    BigDecimal idx = gspcT.divide(p.gspcBase, MathContext.DECIMAL64);
                    BigDecimal fx = usdT.divide(p.usdBase, MathContext.DECIMAL64);
                    sp = sp.add(p.costTl.multiply(idx).multiply(fx)); anyS = true;
                }
                for (var e : benchMaps.entrySet()) {
                    String id = e.getKey();
                    BigDecimal vt = benchT.get(id);
                    BigDecimal base = p.benchBase.get(id);
                    if (!positive(vt) || !positive(base)) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(benchNeedsFx.get(id))) {
                        // USD enstrüman: (fiyat oranı) × (USD/TRY oranı). Kur yoksa atla.
                        if (positive(usdT) && positive(p.usdBase)) {
                            BigDecimal idx = vt.divide(base, MathContext.DECIMAL64);
                            BigDecimal fx = usdT.divide(p.usdBase, MathContext.DECIMAL64);
                            extra.merge(id, p.costTl.multiply(idx).multiply(fx), BigDecimal::add);
                        }
                    } else {
                        extra.merge(id, ratio(p.costTl, vt, base), BigDecimal::add);
                    }
                }
            }

            pt.setCost(scale(cost));
            pt.setActual(anyA ? scale(actual) : null);
            pt.setInflation(anyI ? scale(infl) : null);
            pt.setUsInflation(anyUI ? scale(usInfl) : null);
            pt.setGold(anyG ? scale(gold) : null);
            pt.setUsd(anyU ? scale(usd) : null);
            pt.setDeposit(anyD ? scale(dep) : null);
            pt.setBist100(anyB ? scale(bist) : null);
            pt.setBitcoin(anyC ? scale(btc) : null);
            pt.setSp500(anyS ? scale(sp) : null);
            if (!extra.isEmpty()) {
                Map<String, BigDecimal> scaled = new HashMap<>();
                extra.forEach((k, v) -> scaled.put(k, scale(v)));
                pt.setExtra(scaled);
            }
            points.add(pt);
        }

        List<String> avail = new ArrayList<>();
        if (actualAvail) avail.add("actual");
        if (inflAvail) avail.add("inflation");
        if (usCpiAvail) avail.add("usInflation");
        if (goldAvail) avail.add("gold");
        if (usdAvail) avail.add("usd");
        if (depAvail) avail.add("deposit");
        if (bistAvail) avail.add("bist100");
        if (btcAvail) avail.add("bitcoin");
        if (sp500Avail) avail.add("sp500");
        result.setAvailableBenchmarks(new ArrayList<>(benchMaps.keySet()));

        // Tüm portföyde, paranın çoğu sonradan girdiyse baştaki çok küçük sermayeli kuyruk
        // grafiği yanıltır (ör. en eski tek pozisyonla başlar) → bu kuyruğu kes.
        if (!single) {
            points = trimLeadingLowCapital(points);
        }

        result.setPoints(points);
        result.setAvailableScenarios(avail);
        return result;
    }

    private static boolean matches(PortfolioHoldingResponse h, String assetType, String symbol) {
        return h.getAssetType() != null
                && h.getAssetType().name().equalsIgnoreCase(assetType.trim())
                && h.getSymbol() != null
                && h.getSymbol().equalsIgnoreCase(symbol.trim());
    }

    private NavigableMap<LocalDate, BigDecimal> safePrice(AssetType type, String symbol,
                                                          LocalDate from, LocalDate to) {
        try {
            return pricePort.fetchDailyClosePrices(type, symbol, from, to).orElse(null);
        } catch (Exception e) {
            log.debug("What-if seri fiyatı alınamadı {} {}: {}", type, symbol, e.getMessage());
            return null;
        }
    }

    private List<DateRate> depositRates(LocalDate from, LocalDate to) {
        List<DateRate> rates = new ArrayList<>();
        try {
            List<EconomySeriesPoint> series = economyDataPort.fetchSeries(DEPOSIT_RATE_CODE, from, to);
            if (series != null) {
                for (EconomySeriesPoint p : series) {
                    if (p.getValue() != null) {
                        rates.add(new DateRate(toLocalDate(p.getUnixTime()), p.getValue().doubleValue()));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("What-if mevduat serisi alınamadı: {}", e.getMessage());
        }
        return rates;
    }

    /**
     * Baştaki, yatırılan sermayenin toplamın %10'undan azını temsil eden noktaları atar
     * (paranın çoğu sonradan girdiğinde grafiğin uzun düşük-sermaye kuyruğunu temizler).
     * En az 2 nokta korunur.
     */
    private static List<WhatIfSeriesPoint> trimLeadingLowCapital(List<WhatIfSeriesPoint> pts) {
        if (pts.size() < 3) {
            return pts;
        }
        BigDecimal finalCost = pts.get(pts.size() - 1).getCost();
        if (finalCost == null || finalCost.signum() <= 0) {
            return pts;
        }
        BigDecimal threshold = finalCost.multiply(BigDecimal.valueOf(0.10));
        int start = 0;
        for (int i = 0; i < pts.size(); i++) {
            BigDecimal c = pts.get(i).getCost();
            if (c != null && c.compareTo(threshold) >= 0) {
                start = i;
                break;
            }
        }
        if (start > pts.size() - 2) {
            start = pts.size() - 2; // en az 2 nokta kalsın
        }
        if (start <= 0) {
            return pts;
        }
        return new ArrayList<>(pts.subList(start, pts.size()));
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    /** cost × (value / base) — DECIMAL64. */
    private static BigDecimal ratio(BigDecimal cost, BigDecimal value, BigDecimal base) {
        return cost.multiply(value).divide(base, MathContext.DECIMAL64);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal floorVal(NavigableMap<LocalDate, BigDecimal> map, LocalDate date) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        var e = map.floorEntry(date);
        if (e == null) {
            e = map.firstEntry();
        }
        return e != null ? e.getValue() : null;
    }

    /** earliest, sonra her ayın ilk günü, son olarak bugün. */
    private static List<LocalDate> monthlyDates(LocalDate earliest, LocalDate today) {
        List<LocalDate> dates = new ArrayList<>();
        dates.add(earliest);
        java.time.YearMonth ym = java.time.YearMonth.from(earliest).plusMonths(1);
        while (ym.atDay(1).isBefore(today)) {
            dates.add(ym.atDay(1));
            ym = ym.plusMonths(1);
        }
        if (!dates.get(dates.size() - 1).equals(today)) {
            dates.add(today);
        }
        return dates;
    }

    private static List<EconomySeriesPoint> safe(java.util.function.Supplier<List<EconomySeriesPoint>> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Senaryolar ────────────────────────────────────────────────────────────

    private WhatIfScenario inflationScenario(List<Pos> positions, BigDecimal costBase, LocalDate today) {
        final List<EconomySeriesPoint> tufe;
        try {
            tufe = deflator.tufeSeries();
        } catch (Exception e) {
            return unavailable("inflation", "Enflasyon (TÜFE)");
        }
        if (tufe == null || tufe.isEmpty()) {
            return unavailable("inflation", "Enflasyon (TÜFE)");
        }
        Optional<BigDecimal> nowIdx = deflator.indexValueAt(tufe, today);
        if (nowIdx.isEmpty()) {
            return unavailable("inflation", "Enflasyon (TÜFE)");
        }
        BigDecimal todayIndex = nowIdx.get();
        return accumulate("inflation", "Enflasyon (TÜFE)", positions, costBase, date -> {
            Optional<BigDecimal> atIdx = deflator.indexValueAt(tufe, date);
            if (atIdx.isPresent() && atIdx.get().signum() > 0) {
                return todayIndex.divide(atIdx.get(), MathContext.DECIMAL64);
            }
            return BigDecimal.ONE; // alış o kadar yeni ki enflasyon henüz birikmedi → nakit gibi
        });
    }

    private WhatIfScenario priceScenario(String key, String label, AssetType assetType, String symbol,
                                         List<Pos> positions, BigDecimal costBase,
                                         LocalDate from, LocalDate today) {
        NavigableMap<LocalDate, BigDecimal> series;
        try {
            series = pricePort.fetchDailyClosePrices(assetType, symbol, from, today).orElse(null);
        } catch (Exception e) {
            log.debug("What-if {} fiyat serisi alınamadı: {}", key, e.getMessage());
            series = null;
        }
        if (series == null || series.isEmpty()) {
            return unavailable(key, label);
        }
        final NavigableMap<LocalDate, BigDecimal> s = series;
        final BigDecimal todayPrice = s.lastEntry().getValue();
        if (todayPrice == null || todayPrice.signum() <= 0) {
            return unavailable(key, label);
        }
        return accumulate(key, label, positions, costBase, date -> {
            var entry = s.floorEntry(date);
            if (entry == null) {
                entry = s.firstEntry();
            }
            if (entry == null || entry.getValue() == null || entry.getValue().signum() <= 0) {
                return BigDecimal.ONE;
            }
            return todayPrice.divide(entry.getValue(), MathContext.DECIMAL64);
        });
    }

    private WhatIfScenario depositScenario(List<Pos> positions, BigDecimal costBase,
                                           LocalDate from, LocalDate today) {
        List<EconomySeriesPoint> series;
        try {
            series = economyDataPort.fetchSeries(DEPOSIT_RATE_CODE, from, today);
        } catch (Exception e) {
            log.debug("What-if mevduat serisi alınamadı: {}", e.getMessage());
            series = null;
        }
        if (series == null || series.isEmpty()) {
            return unavailable("deposit", "Mevduat");
        }
        List<DateRate> rates = new ArrayList<>();
        for (EconomySeriesPoint p : series) {
            if (p.getValue() != null) {
                rates.add(new DateRate(toLocalDate(p.getUnixTime()), p.getValue().doubleValue()));
            }
        }
        if (rates.isEmpty()) {
            return unavailable("deposit", "Mevduat");
        }
        return accumulate("deposit", "Mevduat (brüt)", positions, costBase,
                date -> depositFactor(rates, date, today));
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private WhatIfScenario accumulate(String key, String label, List<Pos> positions,
                                      BigDecimal costBase, Function<LocalDate, BigDecimal> factorFn) {
        BigDecimal value = BigDecimal.ZERO;
        for (Pos p : positions) {
            BigDecimal f = factorFn.apply(p.date());
            if (f == null) {
                f = BigDecimal.ONE;
            }
            value = value.add(p.costTl().multiply(f));
        }
        BigDecimal returnPct = costBase.signum() > 0 ? pct(value, costBase) : null;
        return new WhatIfScenario(key, label,
                value.setScale(MONEY_SCALE, RoundingMode.HALF_UP), returnPct, true, null);
    }

    /** Haftalık yıllık mevduat faizinden bileşik büyüme faktörü ({@code from}'dan bugüne). */
    private static BigDecimal depositFactor(List<DateRate> rates, LocalDate from, LocalDate today) {
        if (from.isAfter(today) || rates.isEmpty()) {
            return BigDecimal.ONE;
        }
        double factor = 1.0;
        LocalDate cursor = from;
        double currentRate = rates.get(0).rate();
        for (DateRate r : rates) {
            if (!r.date().isAfter(from)) {
                currentRate = r.rate(); // from'dan önceki/eşit en güncel oran
            } else {
                break;
            }
        }
        for (DateRate r : rates) {
            if (!r.date().isAfter(from)) {
                continue;
            }
            if (!r.date().isBefore(today)) {
                break;
            }
            long days = ChronoUnit.DAYS.between(cursor, r.date());
            if (days > 0) {
                factor *= Math.pow(1 + currentRate / 100.0, days / 365.0);
            }
            cursor = r.date();
            currentRate = r.rate();
        }
        long tail = ChronoUnit.DAYS.between(cursor, today);
        if (tail > 0) {
            factor *= Math.pow(1 + currentRate / 100.0, tail / 365.0);
        }
        return BigDecimal.valueOf(factor);
    }

    private static LocalDate toLocalDate(long unixSeconds) {
        return Instant.ofEpochSecond(unixSeconds).atZone(TR_ZONE).toLocalDate();
    }

    private static BigDecimal pct(BigDecimal value, BigDecimal base) {
        return value.divide(base, MathContext.DECIMAL64)
                .subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static WhatIfScenario unavailable(String key, String label) {
        return new WhatIfScenario(key, label, null, null, false, "Veri yok");
    }
}
