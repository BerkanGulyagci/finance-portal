package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.market.application.economy.port.EconomyDataPort;
import com.finance.portal.market.application.index.BistIndexCatalog;
import com.finance.portal.market.application.indicator.BistIndexService;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.portfolio.application.port.PortfolioHistoricalPricePort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Tür-bağımsız tarihsel fiyat (TL) endpoint'i — herhangi bir enstrümanı (hisse/kripto/altın/döviz/
 * fon/emtia/DİBS/vadeli) tek tip günlük TL kapanış serisiyle döndürür. Ayrıca {@code INDICATOR}
 * sözde-türüyle enflasyon/faiz benchmark'ları (TÜFE / ABD Enflasyonu / Mevduat) **endeks serisi**
 * olarak verilir (kıyas grafiği ilk değere göre normalize ettiği için kümülatif % çıkar).
 * Hisse karşılaştırma sayfasında "herhangi bir şeyle kıyas" için kullanılır.
 */
@RestController
@RequestMapping("/api/v1/market")
public class MarketPriceHistoryController {

    private static final ZoneId TR_ZONE = ZoneId.of("Europe/Istanbul");
    private static final String DEPOSIT_RATE_CODE = "TP.TRY.MT02";

    private final PortfolioHistoricalPricePort pricePort;
    private final InflationDeflatorService deflator;
    private final EconomyDataPort economyDataPort;
    private final BistIndexService bistIndexService;
    private final StockQueryService stockQueryService;

    public MarketPriceHistoryController(PortfolioHistoricalPricePort pricePort,
                                        InflationDeflatorService deflator,
                                        EconomyDataPort economyDataPort,
                                        BistIndexService bistIndexService,
                                        StockQueryService stockQueryService) {
        this.pricePort = pricePort;
        this.deflator = deflator;
        this.economyDataPort = economyDataPort;
        this.bistIndexService = bistIndexService;
        this.stockQueryService = stockQueryService;
    }

    @GetMapping("/price-history")
    public ResponseEntity<ApiResponse<PriceHistoryResponse>> getPriceHistory(
            @RequestParam String assetType,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "1Y") String range) {

        LocalDate today = LocalDate.now();
        LocalDate from = switch (range == null ? "1Y" : range.trim().toUpperCase()) {
            case "1D", "5D", "1W" -> today.minusDays(7);
            case "1M" -> today.minusMonths(1);
            case "3M" -> today.minusMonths(3);
            case "6M" -> today.minusMonths(6);
            case "YTD" -> LocalDate.of(today.getYear(), 1, 1);
            case "1Y" -> today.minusYears(1);
            case "5Y" -> today.minusYears(5);
            case "ALL", "MAX" -> today.minusYears(10);
            default -> today.minusYears(1);
        };

        // Enflasyon/faiz benchmark'ları
        if ("INDICATOR".equalsIgnoreCase(assetType.trim())) {
            return ResponseEntity.ok(ApiResponse.success(buildIndicator(symbol, from, today)));
        }

        List<Long> timestamps = new ArrayList<>();
        List<BigDecimal> closePrices = new ArrayList<>();
        try {
            AssetType type = AssetType.valueOf(assetType.trim().toUpperCase());
            Optional<NavigableMap<LocalDate, BigDecimal>> series =
                    pricePort.fetchDailyClosePrices(type, symbol, from, today);
            if (series.isPresent()) {
                series.get().forEach((day, close) -> {
                    if (close != null) {
                        timestamps.add(epoch(day));
                        closePrices.add(close);
                    }
                });
            }
        } catch (Exception ignored) {
            // geçersiz tür / veri yok → boş seri
        }

        return ResponseEntity.ok(ApiResponse.success(new PriceHistoryResponse(timestamps, closePrices)));
    }

    // ── Benchmark endeks serileri ───────────────────────────────────────────────

    private PriceHistoryResponse buildIndicator(String symbol, LocalDate from, LocalDate to) {
        List<Long> ts = new ArrayList<>();
        List<BigDecimal> cp = new ArrayList<>();
        String upper = symbol.trim().toUpperCase(Locale.ROOT);
        try {
            switch (upper) {
                case "TUFE" -> {
                    List<EconomySeriesPoint> tufe = safe(deflator.tufeSeries());
                    for (EconomySeriesPoint p : tufe) {
                        LocalDate d = toDate(p.getUnixTime());
                        if (p.getValue() != null && p.getValue().signum() > 0
                                && !d.isBefore(from.withDayOfMonth(1)) && !d.isAfter(to)) {
                            ts.add(epoch(d));
                            cp.add(p.getValue());
                        }
                    }
                }
                case "USCPI_TRY" -> emitUsCpiTry(from, to, ts, cp);
                case "DEPOSIT" -> emitDeposit(from, to, ts, cp);
                default -> {
                    // BIST endeksleri (TÜM katalog: XU100/XBANK/XGIDA/… ~41 adet) — hisse grafik yolu
                    // üzerinden günlük kapanış serisi (saatlik fallback'li → sektör endekslerinde de dolu gelir).
                    String code = upper.endsWith(".IS") ? upper.substring(0, upper.length() - 3) : upper;
                    if (BistIndexCatalog.byCode(code) != null) {
                        emitIndexViaStockChart(code + ".IS", from, to, ts, cp);
                    } else if (bistIndexService.supports(upper)) {
                        emitBistIndex(upper, from, to, ts, cp);
                    }
                }
            }
        } catch (Exception ignored) {
            // veri yoksa boş
        }
        return new PriceHistoryResponse(ts, cp);
    }

    /**
     * BIST endeks serisini {@link BistIndexService} üzerinden çeker (^XU100, ^XU030, ^XU050 — Yahoo).
     * Servis 60 dk önbellek + hata-toleranslı; çağıran (frontend ticker) boş yanıtta öğeyi atlar.
     */
    private void emitBistIndex(String upperSymbol, LocalDate from, LocalDate to,
                               List<Long> ts, List<BigDecimal> cp) {
        Optional<YahooChartSnapshot> snapshot = bistIndexService.fetchChart(upperSymbol, from, to);
        if (snapshot.isEmpty()) {
            return;
        }
        YahooChartSnapshot chart = snapshot.get();
        List<Long> stamps = chart.getTimestamps();
        YahooQuoteSeries quote = chart.getQuote();
        if (stamps == null || stamps.isEmpty() || quote == null || quote.getClose() == null) {
            return;
        }
        List<BigDecimal> closes = quote.getClose();
        int n = Math.min(stamps.size(), closes.size());
        for (int i = 0; i < n; i++) {
            Long sec = stamps.get(i);
            BigDecimal close = closes.get(i);
            if (sec == null || close == null || close.signum() <= 0) {
                continue;
            }
            LocalDate d = toDate(sec);
            if (d.isBefore(from) || d.isAfter(to)) {
                continue;
            }
            ts.add(sec);
            cp.add(close);
        }
    }

    /**
     * BIST endeks serisini hisse grafik yolundan ({@link StockQueryService#getStockChartWithParams}) çeker —
     * bu yol saatlik fallback'li olduğu için günlük barı olmayan sektör endekslerinde de dolu seri döner
     * (BistIndexService yalnız XU100/030/050 destekliyordu; bu TÜM katalog endekslerini kapsar).
     */
    private void emitIndexViaStockChart(String symbol, LocalDate from, LocalDate to, List<Long> ts, List<BigDecimal> cp) {
        try {
            String range = BistIndexService.pickYahooRange(from, to);
            StockChartResponse chart = stockQueryService.getStockChartWithParams(symbol, range, "1d");
            List<Long> stamps = chart.getTimestamps();
            List<BigDecimal> closes = chart.getClosePrices();
            if (stamps == null || closes == null) {
                return;
            }
            int n = Math.min(stamps.size(), closes.size());
            for (int i = 0; i < n; i++) {
                Long sec = stamps.get(i);
                BigDecimal close = closes.get(i);
                if (sec == null || close == null || close.signum() <= 0) {
                    continue;
                }
                LocalDate d = toDate(sec);
                if (d.isBefore(from) || d.isAfter(to)) {
                    continue;
                }
                ts.add(sec);
                cp.add(close);
            }
        } catch (RuntimeException ignored) {
            // veri yok → boş seri
        }
    }

    /** ABD enflasyonu (TL alım gücü) = ABD CPI endeksi × USD/TRY — aylık örnekler. */
    private void emitUsCpiTry(LocalDate from, LocalDate to, List<Long> ts, List<BigDecimal> cp) {
        List<EconomySeriesPoint> usCpi = safe(deflator.usCpiSeries());
        if (usCpi.isEmpty()) {
            return;
        }
        NavigableMap<LocalDate, BigDecimal> usd =
                pricePort.fetchDailyClosePrices(AssetType.FX, "USD", from, to).orElse(null);
        if (usd == null || usd.isEmpty()) {
            return;
        }
        YearMonth m = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!m.isAfter(end)) {
            LocalDate d = m.atDay(1).isBefore(from) ? from : m.atDay(1);
            if (d.isAfter(to)) {
                break;
            }
            Optional<BigDecimal> cpi = deflator.indexValueAt(usCpi, d);
            var fx = usd.floorEntry(d);
            if (fx == null) {
                fx = usd.firstEntry();
            }
            if (cpi.isPresent() && cpi.get().signum() > 0 && fx != null
                    && fx.getValue() != null && fx.getValue().signum() > 0) {
                ts.add(epoch(d));
                cp.add(cpi.get().multiply(fx.getValue()));
            }
            m = m.plusMonths(1);
        }
    }

    /** TL mevduat faizinden bileşik endeks (100'den başlar) — aylık örnekler. */
    private void emitDeposit(LocalDate from, LocalDate to, List<Long> ts, List<BigDecimal> cp) {
        TreeMap<LocalDate, Double> rates = new TreeMap<>();
        try {
            List<EconomySeriesPoint> series = economyDataPort.fetchSeries(DEPOSIT_RATE_CODE, from.minusDays(20), to);
            if (series != null) {
                for (EconomySeriesPoint p : series) {
                    if (p.getValue() != null) {
                        rates.put(toDate(p.getUnixTime()), p.getValue().doubleValue());
                    }
                }
            }
        } catch (Exception ignored) {
            // boş
        }
        if (rates.isEmpty()) {
            return;
        }
        YearMonth m = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!m.isAfter(end)) {
            LocalDate d = m.atDay(1).isBefore(from) ? from : m.atDay(1);
            if (d.isAfter(to)) {
                break;
            }
            double factor = depositCompound(rates, from, d);
            ts.add(epoch(d));
            cp.add(BigDecimal.valueOf(100.0 * factor));
            m = m.plusMonths(1);
        }
        // Son nokta (bugün) eklenmediyse ekle
        if (ts.isEmpty() || !toDate(ts.get(ts.size() - 1)).equals(to)) {
            ts.add(epoch(to));
            cp.add(BigDecimal.valueOf(100.0 * depositCompound(rates, from, to)));
        }
    }

    /** {@code from}'dan {@code target}'a haftalık faiz serisinden bileşik büyüme. */
    private static double depositCompound(TreeMap<LocalDate, Double> rates, LocalDate from, LocalDate target) {
        if (!target.isAfter(from)) {
            return 1.0;
        }
        double factor = 1.0;
        LocalDate cursor = from;
        for (var e : rates.tailMap(from, false).headMap(target, false).entrySet()) {
            Double rate = rateAtOrBefore(rates, cursor);
            long days = ChronoUnit.DAYS.between(cursor, e.getKey());
            if (days > 0 && rate != null) {
                factor *= Math.pow(1 + rate / 100.0, days / 365.0);
            }
            cursor = e.getKey();
        }
        Double rate = rateAtOrBefore(rates, cursor);
        long days = ChronoUnit.DAYS.between(cursor, target);
        if (days > 0 && rate != null) {
            factor *= Math.pow(1 + rate / 100.0, days / 365.0);
        }
        return factor;
    }

    private static Double rateAtOrBefore(TreeMap<LocalDate, Double> rates, LocalDate d) {
        var e = rates.floorEntry(d);
        if (e == null) {
            e = rates.firstEntry();
        }
        return e == null ? null : e.getValue();
    }

    private static List<EconomySeriesPoint> safe(List<EconomySeriesPoint> list) {
        return list != null ? list : List.of();
    }

    private static long epoch(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    private static LocalDate toDate(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(TR_ZONE).toLocalDate();
    }

    public record PriceHistoryResponse(List<Long> timestamps, List<BigDecimal> closePrices) {}
}
