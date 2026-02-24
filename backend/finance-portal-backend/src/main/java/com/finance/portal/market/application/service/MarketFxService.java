package com.finance.portal.market.application.service;

import com.finance.portal.market.infrastructure.external.fx.OpenFxClient;
import com.finance.portal.market.infrastructure.external.fx.TcmbFxClient;
import com.finance.portal.market.infrastructure.external.fx.dto.OpenErApiResponseDto;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbCurrencyDto;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbExchangeRatesDto;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import com.finance.portal.market.presentation.dto.FxRateItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MarketFxService {

    private static final Logger logger = LoggerFactory.getLogger(MarketFxService.class);

    private final TcmbFxClient tcmbFxClient;
    private final OpenFxClient openFxClient;

    public MarketFxService(TcmbFxClient tcmbFxClient, OpenFxClient openFxClient) {
        this.tcmbFxClient = tcmbFxClient;
        this.openFxClient = openFxClient;
    }

    @Cacheable(cacheNames = "market.fx.tcmb.latest", key = "#symbols != null ? #symbols : 'default'")
    public FxLatestResponse getTcmbLatestRates(String symbols) {
        TcmbExchangeRatesDto tcmbData = tcmbFxClient.fetchLatestRates();

        Set<String> requestedSymbols = parseSymbols(symbols);

        var currencyStream = tcmbData.getCurrencies().stream();

        if (requestedSymbols != null && !requestedSymbols.isEmpty()) {
            currencyStream = currencyStream
                    .filter(currency -> requestedSymbols.contains(currency.getCurrencyCode()));
        }

        List<FxRateItemDto> rates = currencyStream
                .filter(currency -> currency.getForexBuying() != null && !currency.getForexBuying().trim().isEmpty())
                .filter(currency -> currency.getForexSelling() != null && !currency.getForexSelling().trim().isEmpty())
                .map(this::mapTcmbCurrencyToFxRateItem)
                .collect(Collectors.toList());

        return new FxLatestResponse(
                "tcmb",
                "official",
                "TRY",
                tcmbData.getDate(),
                rates
        );
    }

    @Cacheable(
            cacheNames = "market.fx.open.latest",
            key = "'openfx:' + (#base != null ? #base : \"USD\") + ':' + (#symbols != null ? #symbols : '')"
    )
    public FxLatestResponse getOpenFxLatest(String base, String symbols) {
        String effectiveBase = (base != null && !base.trim().isEmpty())
                ? base.trim().toUpperCase()
                : "USD";

        OpenErApiResponseDto openData = openFxClient.fetchLatestRates(effectiveBase);

        Set<String> requestedSymbols = parseSymbols(symbols);

        Map<String, Double> conversionRates = openData.getRates();
        if (conversionRates == null || conversionRates.isEmpty()) {
            throw new IllegalStateException("Open FX API returned no conversion rates");
        }

        var rateStream = conversionRates.entrySet().stream();

        if (requestedSymbols != null && !requestedSymbols.isEmpty()) {
            rateStream = rateStream
                    .filter(entry -> requestedSymbols.contains(entry.getKey()));
        }

        List<FxRateItemDto> rates = rateStream
                .filter(entry -> entry.getValue() != null)
                .map(entry -> new FxRateItemDto(
                        entry.getKey(),
                        BigDecimal.valueOf(entry.getValue()),
                        BigDecimal.valueOf(entry.getValue()),
                        1
                ))
                .collect(Collectors.toList());

        return new FxLatestResponse(
                "openfx",
                "global",
                openData.getBaseCode() != null ? openData.getBaseCode() : effectiveBase,
                openData.getTimeLastUpdateUtc(),
                rates
        );
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

    private FxRateItemDto mapTcmbCurrencyToFxRateItem(TcmbCurrencyDto currency) {
        try {
            BigDecimal buy = new BigDecimal(currency.getForexBuying());
            BigDecimal sell = new BigDecimal(currency.getForexSelling());
            int unit = currency.getUnit() != null ? currency.getUnit() : 1;

            return new FxRateItemDto(
                    currency.getCurrencyCode(),
                    buy,
                    sell,
                    unit
            );
        } catch (NumberFormatException ex) {
            logger.warn("Failed to parse TCMB currency data for {}: {}", 
                    currency.getCurrencyCode(), ex.getMessage());
            return null;
        }
    }
}
