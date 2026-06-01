package com.finance.portal.market.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.portal.common.infrastructure.cache.LastKnownGoodCache;
import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.fx.model.OpenFxFeed;
import com.finance.portal.market.application.fx.model.TcmbFxCurrencyRow;
import com.finance.portal.market.application.fx.model.TcmbFxFeed;
import com.finance.portal.market.application.fx.port.OpenFxPort;
import com.finance.portal.market.application.fx.port.TcmbFxHistoryPort;
import com.finance.portal.market.application.fx.port.TcmbFxPort;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MarketFxService {

    private static final Logger logger = LoggerFactory.getLogger(MarketFxService.class);
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    /** Hızlı (intraday) veri için LKG ömrü — kaynak çökerse en fazla bu kadar eski kur gösterilir. */
    private static final Duration FX_LKG_TTL = Duration.ofDays(3);

    private final TcmbFxPort tcmbFxPort;
    private final OpenFxPort openFxPort;
    private final TcmbFxHistoryPort tcmbFxHistoryPort;
    private final LastKnownGoodCache lkg;

    public MarketFxService(TcmbFxPort tcmbFxPort, OpenFxPort openFxPort, TcmbFxHistoryPort tcmbFxHistoryPort,
                           LastKnownGoodCache lkg) {
        this.tcmbFxPort = tcmbFxPort;
        this.openFxPort = openFxPort;
        this.tcmbFxHistoryPort = tcmbFxHistoryPort;
        this.lkg = lkg;
    }

    @Cacheable(cacheNames = "market.fx.tcmb.latest", key = "#symbols != null ? #symbols : 'default'")
    @WithSpan("MarketFxService.getTcmbLatestRates")
    public FxLatestRates getTcmbLatestRates(@SpanAttribute("fx.symbols") String symbols) {
        // TCMB çökerse/boş dönerse son başarılı feed'i servis et (kullanıcı 5xx yerine eski kur görür).
        TcmbFxFeed tcmbData = lkg.resilient("fx.tcmb.feed", FX_LKG_TTL,
                new TypeReference<TcmbFxFeed>() {}, tcmbFxPort::fetchLatestRates);
        Set<String> requested = parseSymbols(symbols);

        var stream = tcmbData.getCurrencies().stream();
        if (requested != null && !requested.isEmpty()) {
            stream = stream.filter(c -> requested.contains(c.getCurrencyCode()));
        }

        List<FxRateItem> rates = stream
                .filter(c -> c.getForexBuying() != null && !c.getForexBuying().trim().isEmpty())
                .filter(c -> c.getForexSelling() != null && !c.getForexSelling().trim().isEmpty())
                .map(this::mapTcmbCurrency)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return new FxLatestRates("tcmb", "official", "TRY", tcmbData.getDate(), rates);
    }

    /**
     * Warm-up için: TCMB güncel kur cache'ini ('default' key) atomik tazeler.
     * {@code MarketListCacheWarmupService} periyodik çağırır → kullanıcı isteği soğuk TCMB çekimine
     * HİÇ düşmez (6sa TTL boyunca hep sıcak; TCMB her TL-çevriminin temeli). Self-invoke ile
     * read metodunun @Cacheable'ı bypass edilir; @CachePut taze sonucu read ile AYNI 'default'
     * anahtarına atomik yazar (evict→reload boşluğu yok). LKG ikinci savunma hattı olarak altta kalır.
     */
    @CachePut(cacheNames = "market.fx.tcmb.latest", key = "'default'")
    public FxLatestRates refreshTcmbLatest() {
        return getTcmbLatestRates(null);
    }

    @Cacheable(
            cacheNames = "market.fx.open.latest",
            key = "'openfx:' + (#base != null ? #base : \"USD\") + ':' + (#symbols != null ? #symbols : '')"
    )
    public FxLatestRates getOpenFxLatest(String base, String symbols) {
        String effectiveBase = (base != null && !base.trim().isEmpty())
                ? base.trim().toUpperCase() : "USD";

        // Open FX kaynağı çökerse/boş dönerse son başarılı feed'i servis et (base'e göre ayrı LKG).
        OpenFxFeed openData = lkg.resilient("fx.open.latest:" + effectiveBase, FX_LKG_TTL,
                new TypeReference<OpenFxFeed>() {}, () -> openFxPort.fetchLatestRates(effectiveBase));
        Set<String> requested = parseSymbols(symbols);

        Map<String, Double> conversionRates = openData.getRates();
        if (conversionRates == null || conversionRates.isEmpty()) {
            throw new IllegalStateException("Open FX API returned no conversion rates");
        }

        var stream = conversionRates.entrySet().stream();
        if (requested != null && !requested.isEmpty()) {
            stream = stream.filter(e -> requested.contains(e.getKey()));
        }

        List<FxRateItem> rates = stream
                .filter(e -> e.getValue() != null)
                .map(e -> new FxRateItem(
                        e.getKey(),
                        BigDecimal.valueOf(e.getValue()),
                        BigDecimal.valueOf(e.getValue()),
                        1))
                .collect(Collectors.toList());

        return new FxLatestRates(
                "openfx", "global",
                openData.getBaseCode() != null ? openData.getBaseCode() : effectiveBase,
                openData.getTimeLastUpdateUtc(),
                rates);
    }

    @Cacheable(cacheNames = "market.fx.history", key = "#symbol + ':' + #range")
    @WithSpan("MarketFxService.getFxHistory")
    public FxHistory getFxHistory(@SpanAttribute("fx.symbol") String symbol,
                                   @SpanAttribute("fx.range") String range) {
        String sym = symbol.toUpperCase();
        LocalDate today = LocalDate.now(ISTANBUL);
        LocalDate from = rangeToFromDate(range, today);

        // TCMB geçmiş kuru çökerse/boş dönerse son başarılı seriyi servis et (sembol+aralık başına LKG).
        List<FxHistoryPoint> points = lkg.resilient(
                "fx.history:" + sym + ":" + (range == null ? "1M" : range.toUpperCase()), FX_LKG_TTL,
                new TypeReference<List<FxHistoryPoint>>() {}, () -> tcmbFxHistoryPort.fetchHistory(sym, from, today));
        return new FxHistory(sym, "TRY", range, points);
    }

    private LocalDate rangeToFromDate(String range, LocalDate today) {
        return switch (range == null ? "1M" : range.toUpperCase()) {
            case "1W" -> today.minusWeeks(1);
            case "3M" -> today.minusMonths(3);
            case "6M" -> today.minusMonths(6);
            case "1Y" -> today.minusYears(1);
            case "ALL" -> today.minusYears(5);
            default -> today.minusMonths(1);
        };
    }

    private Set<String> parseSymbols(String symbols) {
        if (symbols == null || symbols.trim().isEmpty()) {
            return null;
        }
        return java.util.Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private FxRateItem mapTcmbCurrency(TcmbFxCurrencyRow currency) {
        try {
            BigDecimal buy = new BigDecimal(currency.getForexBuying());
            BigDecimal sell = new BigDecimal(currency.getForexSelling());
            int unit = currency.getUnit() != null ? currency.getUnit() : 1;
            // Efektif (banknot) kurları TCMB'de bazı dövizler için boş gelebilir; varsa ekle.
            BigDecimal effectiveBuy = parseOptionalDecimal(currency.getBanknoteBuying());
            BigDecimal effectiveSell = parseOptionalDecimal(currency.getBanknoteSelling());
            return new FxRateItem(currency.getCurrencyCode(), buy, sell, unit, effectiveBuy, effectiveSell);
        } catch (NumberFormatException ex) {
            logger.warn("Failed to parse TCMB currency {}: {}", currency.getCurrencyCode(), ex.getMessage());
            return null;
        }
    }

    private BigDecimal parseOptionalDecimal(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
