package com.finance.portal.market.application.service;

import com.finance.portal.market.infrastructure.external.fx.OpenFxClient;
import com.finance.portal.market.infrastructure.external.fx.TcmbFxClient;
import com.finance.portal.market.infrastructure.external.fx.TcmbFxHistoryClient;
import com.finance.portal.market.infrastructure.external.fx.dto.OpenErApiResponseDto;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbCurrencyDto;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbExchangeRatesDto;
import com.finance.portal.market.presentation.dto.FxHistoryPoint;
import com.finance.portal.market.presentation.dto.FxHistoryResponse;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import com.finance.portal.market.presentation.dto.FxRateItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    private final TcmbFxClient tcmbFxClient;
    private final OpenFxClient openFxClient;
    private final TcmbFxHistoryClient tcmbFxHistoryClient;

    public MarketFxService(TcmbFxClient tcmbFxClient, OpenFxClient openFxClient,
                           TcmbFxHistoryClient tcmbFxHistoryClient) {
        this.tcmbFxClient = tcmbFxClient;
        this.openFxClient = openFxClient;
        this.tcmbFxHistoryClient = tcmbFxHistoryClient;
    }

    // ── TCMB Anlık ───────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.fx.tcmb.latest", key = "#symbols != null ? #symbols : 'default'")
    public FxLatestResponse getTcmbLatestRates(String symbols) {
        TcmbExchangeRatesDto tcmbData = tcmbFxClient.fetchLatestRates();
        Set<String> requested = parseSymbols(symbols);

        var stream = tcmbData.getCurrencies().stream();
        if (requested != null && !requested.isEmpty()) {
            stream = stream.filter(c -> requested.contains(c.getCurrencyCode()));
        }

        List<FxRateItemDto> rates = stream
                .filter(c -> c.getForexBuying()  != null && !c.getForexBuying().trim().isEmpty())
                .filter(c -> c.getForexSelling() != null && !c.getForexSelling().trim().isEmpty())
                .map(this::mapTcmbCurrency)
                .collect(Collectors.toList());

        return new FxLatestResponse("tcmb", "official", "TRY", tcmbData.getDate(), rates);
    }

    // ── Open Exchange Rates Anlık ─────────────────────────────────────────────

    @Cacheable(
        cacheNames = "market.fx.open.latest",
        key = "'openfx:' + (#base != null ? #base : \"USD\") + ':' + (#symbols != null ? #symbols : '')"
    )
    public FxLatestResponse getOpenFxLatest(String base, String symbols) {
        String effectiveBase = (base != null && !base.trim().isEmpty())
                ? base.trim().toUpperCase() : "USD";

        OpenErApiResponseDto openData = openFxClient.fetchLatestRates(effectiveBase);
        Set<String> requested = parseSymbols(symbols);

        Map<String, Double> conversionRates = openData.getRates();
        if (conversionRates == null || conversionRates.isEmpty()) {
            throw new IllegalStateException("Open FX API returned no conversion rates");
        }

        var stream = conversionRates.entrySet().stream();
        if (requested != null && !requested.isEmpty()) {
            stream = stream.filter(e -> requested.contains(e.getKey()));
        }

        List<FxRateItemDto> rates = stream
                .filter(e -> e.getValue() != null)
                .map(e -> new FxRateItemDto(
                        e.getKey(),
                        BigDecimal.valueOf(e.getValue()),
                        BigDecimal.valueOf(e.getValue()),
                        1))
                .collect(Collectors.toList());

        return new FxLatestResponse(
                "openfx", "global",
                openData.getBaseCode() != null ? openData.getBaseCode() : effectiveBase,
                openData.getTimeLastUpdateUtc(),
                rates);
    }

    // ── Tarihsel Veri (TCMB XML Arşivi) ──────────────────────────────────────

    /**
     * TCMB günlük XML arşivinden tarihsel kur verisi.
     * range: 1W, 1M, 3M, 6M, 1Y, ALL
     */
    @Cacheable(cacheNames = "market.fx.history", key = "#symbol + ':' + #range")
    public FxHistoryResponse getFxHistory(String symbol, String range) {
        String sym = symbol.toUpperCase();
        LocalDate today = LocalDate.now(ISTANBUL);
        LocalDate from  = rangeToFromDate(range, today);

        List<FxHistoryPoint> points = tcmbFxHistoryClient.fetchHistory(sym, from, today);
        return new FxHistoryResponse(sym, "TRY", range, points);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDate rangeToFromDate(String range, LocalDate today) {
        return switch (range == null ? "1M" : range.toUpperCase()) {
            case "1W"  -> today.minusWeeks(1);
            case "3M"  -> today.minusMonths(3);
            case "6M"  -> today.minusMonths(6);
            case "1Y"  -> today.minusYears(1);
            case "ALL" -> today.minusYears(5);
            default    -> today.minusMonths(1);
        };
    }

    private Set<String> parseSymbols(String symbols) {
        if (symbols == null || symbols.trim().isEmpty()) return null;
        return java.util.Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private FxRateItemDto mapTcmbCurrency(TcmbCurrencyDto currency) {
        try {
            BigDecimal buy  = new BigDecimal(currency.getForexBuying());
            BigDecimal sell = new BigDecimal(currency.getForexSelling());
            int unit = currency.getUnit() != null ? currency.getUnit() : 1;
            return new FxRateItemDto(currency.getCurrencyCode(), buy, sell, unit);
        } catch (NumberFormatException ex) {
            logger.warn("Failed to parse TCMB currency {}: {}", currency.getCurrencyCode(), ex.getMessage());
            return null;
        }
    }
}
