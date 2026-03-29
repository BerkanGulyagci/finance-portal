package com.finance.portal.market.application.funds.service;

import com.finance.portal.common.infrastructure.exception.ExternalApiException;
import com.finance.portal.common.infrastructure.exception.ResourceNotFoundException;
import com.finance.portal.market.application.funds.model.FundChartPoint;
import com.finance.portal.market.application.funds.model.FundChartResponse;
import com.finance.portal.market.application.funds.model.FundDetail;
import com.finance.portal.market.application.funds.model.FundPageResponse;
import com.finance.portal.market.application.funds.model.FundSummary;
import com.finance.portal.market.application.stock.port.YahooStockPort;
import com.finance.portal.market.infrastructure.external.yahoo.YahooChartResponseDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class FundQueryService {

    private static final Logger logger = LoggerFactory.getLogger(FundQueryService.class);

    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z.]{1,10}$");

    private final YahooStockPort yahooStockPort;
    private final CacheManager cacheManager;
    private final FundSymbolProvider fundSymbolProvider;

    public FundQueryService(YahooStockPort yahooStockPort, CacheManager cacheManager, FundSymbolProvider fundSymbolProvider) {
        this.yahooStockPort = yahooStockPort;
        this.cacheManager = cacheManager;
        this.fundSymbolProvider = fundSymbolProvider;
    }

    public FundSummary getFundSummary(String symbol) {
        validateSymbol(symbol);
        YahooChartResponseDto.Meta meta = fetchMetaOrThrow(symbol);
        return mapToFundSummary(meta);
    }

    @Cacheable(cacheNames = "market.funds.page", key = "'page:' + #page + ':size:' + #size")
    public FundPageResponse getPagedFunds(int page, int size) {
        int totalElements = fundSymbolProvider.getTotalElements();
        List<String> symbols = fundSymbolProvider.getPagedSymbols(page, size);

        List<FundSummary> content = symbols.isEmpty() ? List.of() :
            symbols.stream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> {
                    try { return getFundSummary(symbol); }
                    catch (Exception ex) { logger.warn("Failed fund summary for {}: {}", symbol, ex.getMessage()); return null; }
                }))
                .toList().stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        FundPageResponse response = new FundPageResponse();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(size > 0 ? (int) Math.ceil((double) totalElements / size) : 0);
        return response;
    }

    @Cacheable(
            cacheNames = "market.funds.detail",
            key = "'detail:' + #symbol"
    )
    public FundDetail getFundDetail(String symbol) {
        validateSymbol(symbol);
        YahooChartResponseDto.Meta meta = fetchMetaOrThrow(symbol);

        FundSummary summary = mapToFundSummary(meta);

        FundDetail detail = new FundDetail();
        detail.setSummary(summary);
        detail.setFiftyTwoWeekHigh(meta.getFiftyTwoWeekHigh());
        detail.setFiftyTwoWeekLow(meta.getFiftyTwoWeekLow());
        detail.setRegularMarketTime(meta.getRegularMarketTime());

        return detail;
    }

    @Cacheable(
            cacheNames = "market.funds.chart",
            key = "'symbol:' + #symbol + ':range:' + #range + ':interval:' + #interval"
    )
    @CircuitBreaker(name = "yahooApi", fallbackMethod = "fallbackFundChart")
    public FundChartResponse getFundChart(String symbol, String range, String interval) {
        validateSymbol(symbol);

        String effectiveRange = range != null ? range : "1mo";
        String effectiveInterval = interval != null ? interval : "1d";

        YahooChartResponseDto response = yahooStockPort.fetchChartWithParams(symbol, effectiveRange, effectiveInterval);

        if (response == null
                || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()) {

            logger.info("Fund chart not found for symbol: {}", symbol);
            throw new ResourceNotFoundException("Fund chart not found for symbol: " + symbol);
        }

        YahooChartResponseDto.Result result = response.getChart().getResult().get(0);

        List<Long> timestamps = result.getTimestamp();
        YahooChartResponseDto.Quote quote = null;

        if (result.getIndicators() != null
                && result.getIndicators().getQuote() != null
                && !result.getIndicators().getQuote().isEmpty()) {
            quote = result.getIndicators().getQuote().get(0);
        }

        if (timestamps == null || timestamps.isEmpty() || quote == null) {
            logger.info("Fund chart data missing for symbol: {}", symbol);
            throw new ResourceNotFoundException("Fund chart data not available for symbol: " + symbol);
        }

        List<BigDecimal> opens = quote.getOpen();
        List<BigDecimal> highs = quote.getHigh();
        List<BigDecimal> lows = quote.getLow();
        List<BigDecimal> closes = quote.getClose();
        List<Long> volumes = quote.getVolume();

        List<FundChartPoint> candles = new ArrayList<>();

        for (int i = 0; i < timestamps.size(); i++) {
            Long timestamp = timestamps.get(i);
            BigDecimal open = (opens != null && i < opens.size()) ? opens.get(i) : null;
            BigDecimal high = (highs != null && i < highs.size()) ? highs.get(i) : null;
            BigDecimal low = (lows != null && i < lows.size()) ? lows.get(i) : null;
            BigDecimal close = (closes != null && i < closes.size()) ? closes.get(i) : null;
            Long volume = (volumes != null && i < volumes.size()) ? volumes.get(i) : null;

            if (timestamp != null && open != null && high != null && low != null && close != null) {
                FundChartPoint point = new FundChartPoint();
                point.setTimestamp(timestamp);
                point.setOpen(open);
                point.setHigh(high);
                point.setLow(low);
                point.setClose(close);
                point.setVolume(volume);
                candles.add(point);
            }
        }

        FundChartResponse chartResponse = new FundChartResponse();
        chartResponse.setSymbol(symbol);
        chartResponse.setRange(effectiveRange);
        chartResponse.setInterval(effectiveInterval);
        chartResponse.setCandles(candles);

        return chartResponse;
    }

    private FundChartResponse fallbackFundChart(String symbol, String range, String interval, Throwable ex) {
        logger.error("Fund chart fallback triggered for symbol {} with range {} and interval {}: {}",
                symbol, range, interval, ex.getMessage());

        String cacheKey = "symbol:" + symbol + ":range:" + range + ":interval:" + interval;
        Cache cache = cacheManager.getCache("market.funds.chart");

        if (cache != null) {
            FundChartResponse cached = cache.get(cacheKey, FundChartResponse.class);
            if (cached != null) {
                logger.info("Returning cached fund chart for symbol {} from fallback", symbol);
                return cached;
            }
        }

        throw new ExternalApiException(
                "Yahoo Finance API is temporarily unavailable for fund chart: " + symbol, ex);
    }

    private void validateSymbol(String symbol) {
        if (symbol == null || !SYMBOL_PATTERN.matcher(symbol).matches()) {
            throw new IllegalArgumentException("Invalid symbol format: " + symbol);
        }
    }

    private YahooChartResponseDto.Meta fetchMetaOrThrow(String symbol) {
        YahooChartResponseDto response = yahooStockPort.fetchChart(symbol);

        if (response == null
                || response.getChart() == null
                || response.getChart().getResult() == null
                || response.getChart().getResult().isEmpty()
                || response.getChart().getResult().get(0).getMeta() == null) {

            logger.info("Fund not found for symbol: {}", symbol);
            throw new ResourceNotFoundException("Fund not found for symbol: " + symbol);
        }

        return response.getChart().getResult().get(0).getMeta();
    }

    private FundSummary mapToFundSummary(YahooChartResponseDto.Meta meta) {
        FundSummary summary = new FundSummary();

        summary.setSymbol(meta.getSymbol());
        summary.setName(meta.getLongName());
        summary.setCurrency(meta.getCurrency());
        summary.setExchange(meta.getExchangeName());

        BigDecimal price = defaultBigDecimal(meta.getRegularMarketPrice());
        BigDecimal previousClose = defaultBigDecimal(meta.getPreviousClose());

        BigDecimal change = price.subtract(previousClose);
        BigDecimal changePercent = BigDecimal.ZERO;

        if (previousClose.compareTo(BigDecimal.ZERO) != 0) {
            changePercent = change
                    .divide(previousClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        summary.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        summary.setChange(change.setScale(2, RoundingMode.HALF_UP));
        summary.setChangePercent(changePercent);

        summary.setDayHigh(scaleIfNotNull(meta.getRegularMarketDayHigh()));
        summary.setDayLow(scaleIfNotNull(meta.getRegularMarketDayLow()));
        summary.setVolume(meta.getRegularMarketVolume());
        summary.setAsOf(formatAsOf(meta.getRegularMarketTime()));

        return summary;
    }

    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scaleIfNotNull(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private String formatAsOf(Long epochSeconds) {
        if (epochSeconds == null) {
            return null;
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(epochSeconds),
                ISTANBUL_ZONE
        );
        return dateTime.toString();
    }
}
