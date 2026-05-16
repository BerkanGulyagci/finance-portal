package com.finance.portal.market.application.commodity;

import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.infrastructure.external.yahoo.YahooChartClient;
import com.finance.portal.market.infrastructure.external.yahoo.YahooChartResponseDto;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import com.finance.portal.market.presentation.dto.FxRateItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Yahoo Finance üzerinden global emtia futures verisi.
 * Kıymetli madenler (AU/AG/PT/PD) bu serviste yer almaz.
 *
 * <p>USX dönüşümü: Yahoo currency="USX" ise displayPrice = rawPrice / 100</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YahooCommodityService {

    private static final String SOURCE      = "Yahoo Finance";
    private static final String DATA_TYPE   = "FUTURES_REFERENCE";
    private static final String DISCLAIMER  =
            "Kaynak: Yahoo Finance — vadeli kontrat referans verisi. " +
            "Bu fiyatlar spot piyasa fiyatı değildir.";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final ZoneId     UTC     = ZoneOffset.UTC;

    private final YahooChartClient yahooChartClient;
    private final MarketFxService marketFxService;

    // ── Cache temizleme ───────────────────────────────────────────────────────

    @Scheduled(initialDelay = 60_000, fixedRate = 60_000)
    @CacheEvict(cacheNames = "market.commodity.spot", allEntries = true)
    public void evictSpotCache() {
        log.debug("Commodity spot cache evicted");
    }

    @Scheduled(initialDelay = 600_000, fixedRate = 600_000)
    @CacheEvict(cacheNames = "market.commodity.history", allEntries = true)
    public void evictHistoryCache() {
        log.debug("Commodity history cache evicted");
    }

    // ── Liste ─────────────────────────────────────────────────────────────────

    /**
     * Aktif (enabled=true) tüm emtia tanımlarını döndürür.
     * Fiyat içermez — sadece metadata.
     */
    public List<CommodityDto> listEnabledCommodities() {
        List<CommodityDto> result = new ArrayList<>();
        for (CommoditySymbol cs : CommoditySymbol.values()) {
            if (!cs.isEnabled()) continue;
            result.add(toDto(cs));
        }
        return result;
    }

    // ── Spot ─────────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.commodity.spot", key = "#symbol")
    public CommoditySpotDto getSpot(String symbol) {
        CommoditySymbol cs = CommoditySymbol.fromSymbol(symbol);

        if (!cs.isEnabled()) {
            throw new IllegalArgumentException("Emtia devre dışı: " + symbol);
        }

        log.info("Fetching commodity spot: {}", cs.getSymbol());

        CommoditySpotDto dto = buildBaseSpot(cs);

        try {
            YahooChartResponseDto resp = yahooChartClient.fetchChartWithParams(
                    cs.getSymbol(), "1d", "1m");

            YahooChartResponseDto.Result result = firstResult(resp);
            if (result == null) {
                log.warn("Yahoo returned empty result for {}", cs.getSymbol());
                dto.setStale(true);
                return dto;
            }

            YahooChartResponseDto.Meta meta = result.getMeta();
            String yahooCurrency = meta.getCurrency();
            boolean isCent = "USX".equalsIgnoreCase(yahooCurrency) || cs.isNeedsCentConversion();

            BigDecimal rawPrice  = meta.getRegularMarketPrice();
            BigDecimal prevClose = meta.getPreviousClose();

            dto.setRawPrice(rawPrice);
            dto.setRawCurrency(yahooCurrency != null ? yahooCurrency : "USD");
            dto.setDisplayPrice(convert(rawPrice, isCent));
            dto.setDisplayCurrency("USD");
            dto.setCentConverted(isCent);

            dto.setPreviousClose(convert(prevClose, isCent));
            dto.setDayHigh(convert(meta.getRegularMarketDayHigh(), isCent));
            dto.setDayLow(convert(meta.getRegularMarketDayLow(), isCent));
            dto.setWeekHigh52(convert(meta.getFiftyTwoWeekHigh(), isCent));
            dto.setWeekLow52(convert(meta.getFiftyTwoWeekLow(), isCent));
            dto.setVolume(meta.getRegularMarketVolume());

            // Değişim hesapla
            if (rawPrice != null && prevClose != null && prevClose.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal dispPrice = dto.getDisplayPrice();
                BigDecimal dispPrev  = dto.getPreviousClose();
                if (dispPrice != null && dispPrev != null) {
                    BigDecimal change = dispPrice.subtract(dispPrev).setScale(4, RoundingMode.HALF_UP);
                    BigDecimal pct    = change.divide(dispPrev, 6, RoundingMode.HALF_UP)
                                             .multiply(new BigDecimal("100"))
                                             .setScale(2, RoundingMode.HALF_UP);
                    dto.setChange(change);
                    dto.setChangePercent(pct);
                }
            }

            applyTryDisplayPrices(dto);

            dto.setLastUpdated(LocalDateTime.now(ZoneId.of("Europe/Istanbul")).toString());
            dto.setStale(false);

        } catch (Exception e) {
            log.error("Failed to fetch commodity spot [{}]: {}", cs.getSymbol(), e.getMessage());
            dto.setStale(true);
        }

        return dto;
    }

    // ── History ───────────────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.commodity.history", key = "#symbol + ':' + #range + ':' + #interval")
    public CommodityHistoryResponse getHistory(String symbol, String range, String interval) {
        CommoditySymbol cs = CommoditySymbol.fromSymbol(symbol);

        if (!cs.isEnabled()) {
            throw new IllegalArgumentException("Emtia devre dışı: " + symbol);
        }

        // range/interval mapping
        String[] ri = resolveRangeInterval(range, interval);
        String yahooRange    = ri[0];
        String yahooInterval = ri[1];

        log.info("Fetching commodity history: {} range={} interval={}", cs.getSymbol(), yahooRange, yahooInterval);

        CommodityHistoryResponse resp = new CommodityHistoryResponse();
        resp.setSymbol(cs.getSymbol());
        resp.setDisplayNameTr(cs.getDisplayNameTr());
        resp.setDisplayNameEn(cs.getDisplayNameEn());
        resp.setCategory(cs.getCategory().name());
        resp.setUnit(cs.getUnit());
        resp.setRange(range);
        resp.setInterval(yahooInterval);
        resp.setDisplayCurrency("USD");
        resp.setSource(SOURCE);
        resp.setDataType(DATA_TYPE);
        resp.setDisclaimer(DISCLAIMER);
        resp.setPoints(new ArrayList<>());

        try {
            YahooChartResponseDto yahooResp = yahooChartClient.fetchChartWithParams(
                    cs.getSymbol(), yahooRange, yahooInterval);

            YahooChartResponseDto.Result result = firstResult(yahooResp);
            if (result == null || result.getTimestamp() == null) {
                log.warn("Yahoo returned empty history for {}", cs.getSymbol());
                return resp;
            }

            String yahooCurrency = result.getMeta() != null ? result.getMeta().getCurrency() : null;
            boolean isCent = "USX".equalsIgnoreCase(yahooCurrency) || cs.isNeedsCentConversion();

            List<Long>       timestamps = result.getTimestamp();
            YahooChartResponseDto.Quote quote = firstQuote(result);

            List<CommodityHistoryPointDto> points = new ArrayList<>();

            for (int i = 0; i < timestamps.size(); i++) {
                Long ts = timestamps.get(i);
                if (ts == null) continue;

                BigDecimal rawClose = safeGet(quote != null ? quote.getClose() : null, i);
                if (rawClose == null || rawClose.compareTo(BigDecimal.ZERO) == 0) continue;

                BigDecimal rawOpen  = safeGet(quote != null ? quote.getOpen()  : null, i);
                BigDecimal rawHigh  = safeGet(quote != null ? quote.getHigh()  : null, i);
                BigDecimal rawLow   = safeGet(quote != null ? quote.getLow()   : null, i);
                Long       vol      = safeGetLong(quote != null ? quote.getVolume() : null, i);

                CommodityHistoryPointDto pt = new CommodityHistoryPointDto();
                pt.setTimestamp(ts);
                pt.setDate(epochToDate(ts));

                pt.setRawOpen(rawOpen);
                pt.setRawHigh(rawHigh);
                pt.setRawLow(rawLow);
                pt.setRawClose(rawClose);
                pt.setRawCurrency(yahooCurrency != null ? yahooCurrency : "USD");

                pt.setDisplayOpen(convert(rawOpen, isCent));
                pt.setDisplayHigh(convert(rawHigh, isCent));
                pt.setDisplayLow(convert(rawLow, isCent));
                pt.setDisplayClose(convert(rawClose, isCent));
                pt.setDisplayCurrency("USD");

                pt.setVolume(vol);
                pt.setUnit(cs.getUnit());
                pt.setCentConverted(isCent);

                points.add(pt);
            }

            resp.setPoints(points);
            log.info("Commodity history [{}] {}/{}: {} points", cs.getSymbol(), yahooRange, yahooInterval, points.size());

        } catch (Exception e) {
            log.error("Failed to fetch commodity history [{}]: {}", cs.getSymbol(), e.getMessage());
        }

        return resp;
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────────

    private CommodityDto toDto(CommoditySymbol cs) {
        CommodityDto dto = new CommodityDto();
        dto.setSymbol(cs.getSymbol());
        dto.setDisplayNameTr(cs.getDisplayNameTr());
        dto.setDisplayNameEn(cs.getDisplayNameEn());
        dto.setCategory(cs.getCategory().name());
        dto.setCategoryDisplayTr(cs.getCategory().getDisplayNameTr());
        dto.setUnit(cs.getUnit());
        dto.setNeedsCentConversion(cs.isNeedsCentConversion());
        dto.setEnabled(cs.isEnabled());
        dto.setSource(SOURCE);
        dto.setDataType(DATA_TYPE);
        return dto;
    }

    private CommoditySpotDto buildBaseSpot(CommoditySymbol cs) {
        CommoditySpotDto dto = new CommoditySpotDto();
        dto.setSymbol(cs.getSymbol());
        dto.setDisplayNameTr(cs.getDisplayNameTr());
        dto.setDisplayNameEn(cs.getDisplayNameEn());
        dto.setCategory(cs.getCategory().name());
        dto.setCategoryDisplayTr(cs.getCategory().getDisplayNameTr());
        dto.setUnit(cs.getUnit());
        dto.setSource(SOURCE);
        dto.setDataType(DATA_TYPE);
        dto.setDisplayCurrency("USD");
        dto.setStale(false);
        return dto;
    }

    /** USX → USD dönüşümü. null gelirse null döner. */
    private BigDecimal convert(BigDecimal value, boolean isCent) {
        if (value == null) return null;
        if (isCent) return value.divide(HUNDRED, 6, RoundingMode.HALF_UP);
        return value;
    }

    /** Yahoo USD gösterim fiyatlarını TCMB satış kuru ile TL'ye çevirir (portföy / modal). */
    private void applyTryDisplayPrices(CommoditySpotDto dto) {
        BigDecimal usdTry = resolveTcmbUsdTry();
        if (usdTry == null || dto.getDisplayPrice() == null) {
            return;
        }
        dto.setDisplayPrice(scaleUsdToTry(dto.getDisplayPrice(), usdTry));
        dto.setDisplayCurrency("TRY");
        if (dto.getPreviousClose() != null) {
            dto.setPreviousClose(scaleUsdToTry(dto.getPreviousClose(), usdTry));
        }
        if (dto.getDayHigh() != null) {
            dto.setDayHigh(scaleUsdToTry(dto.getDayHigh(), usdTry));
        }
        if (dto.getDayLow() != null) {
            dto.setDayLow(scaleUsdToTry(dto.getDayLow(), usdTry));
        }
        if (dto.getWeekHigh52() != null) {
            dto.setWeekHigh52(scaleUsdToTry(dto.getWeekHigh52(), usdTry));
        }
        if (dto.getWeekLow52() != null) {
            dto.setWeekLow52(scaleUsdToTry(dto.getWeekLow52(), usdTry));
        }
        if (dto.getChange() != null) {
            dto.setChange(scaleUsdToTry(dto.getChange(), usdTry));
        }
    }

    private BigDecimal scaleUsdToTry(BigDecimal usdAmount, BigDecimal usdTry) {
        if (usdAmount == null || usdTry == null) {
            return null;
        }
        return usdAmount.multiply(usdTry).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveTcmbUsdTry() {
        try {
            FxLatestResponse fx = marketFxService.getTcmbLatestRates("USD");
            if (fx.getRates() == null) {
                return null;
            }
            return fx.getRates().stream()
                    .filter(r -> "USD".equalsIgnoreCase(r.getSymbol()))
                    .map(FxRateItemDto::getSell)
                    .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("TCMB USD/TRY unavailable for commodity conversion: {}", e.getMessage());
            return null;
        }
    }

    private YahooChartResponseDto.Result firstResult(YahooChartResponseDto resp) {
        if (resp == null || resp.getChart() == null) return null;
        List<YahooChartResponseDto.Result> results = resp.getChart().getResult();
        if (results == null || results.isEmpty()) return null;
        return results.get(0);
    }

    private YahooChartResponseDto.Quote firstQuote(YahooChartResponseDto.Result result) {
        if (result.getIndicators() == null) return null;
        List<YahooChartResponseDto.Quote> quotes = result.getIndicators().getQuote();
        if (quotes == null || quotes.isEmpty()) return null;
        return quotes.get(0);
    }

    private BigDecimal safeGet(List<BigDecimal> list, int i) {
        if (list == null || i >= list.size()) return null;
        return list.get(i);
    }

    private Long safeGetLong(List<Long> list, int i) {
        if (list == null || i >= list.size()) return null;
        return list.get(i);
    }

    private String epochToDate(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(UTC)
                .toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * Frontend range → Yahoo range + interval mapping.
     * Mevcut projede kullanılan standartla uyumlu.
     */
    private String[] resolveRangeInterval(String range, String interval) {
        // interval açıkça verilmişse kullan
        if (interval != null && !interval.isBlank()) {
            String yahooRange = switch (range == null ? "1M" : range.toUpperCase()) {
                case "1D"  -> "1d";
                case "1W"  -> "5d";
                case "1M"  -> "1mo";
                case "3M"  -> "3mo";
                case "6M"  -> "6mo";
                case "1Y"  -> "1y";
                case "5Y"  -> "5y";
                default    -> "1mo";
            };
            return new String[]{yahooRange, interval};
        }

        // Otomatik interval seçimi
        return switch (range == null ? "1M" : range.toUpperCase()) {
            case "1D"  -> new String[]{"1d",  "5m"};
            case "1W"  -> new String[]{"5d",  "1h"};
            case "1M"  -> new String[]{"1mo", "1d"};
            case "3M"  -> new String[]{"3mo", "1d"};
            case "6M"  -> new String[]{"6mo", "1d"};
            case "1Y"  -> new String[]{"1y",  "1d"};
            case "5Y"  -> new String[]{"5y",  "1wk"};
            default    -> new String[]{"1mo", "1d"};
        };
    }
}
