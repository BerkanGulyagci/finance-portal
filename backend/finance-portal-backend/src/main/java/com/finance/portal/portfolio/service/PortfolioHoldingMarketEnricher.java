package com.finance.portal.portfolio.service;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.enrich.BondHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.CommodityHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.CryptoHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FutureHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.FxHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.GoldHoldingEnricher;
import com.finance.portal.portfolio.service.enrich.StockHoldingEnricher;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioHistoryPoints;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
import com.finance.portal.portfolio.service.support.RasyonetFundLookup;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Portföy pozisyonları için canlı piyasa alanlarını doldurur. */
@Component
public class PortfolioHoldingMarketEnricher implements HoldingMarketEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(PortfolioHoldingMarketEnricher.class);
    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    private static final int FUND_MONEY_SCALE = 8;

    private final AssetPriceQueryService assetPriceQueryService;
    private final RasyonetFundService rasyonetFundService;
    private final CentralIntegrationLogService integrationLogService;
    private final CryptoHoldingEnricher cryptoHoldingEnricher;
    private final BondHoldingEnricher bondHoldingEnricher;
    private final StockHoldingEnricher stockHoldingEnricher;
    private final FutureHoldingEnricher futureHoldingEnricher;
    private final FxHoldingEnricher fxHoldingEnricher;
    private final GoldHoldingEnricher goldHoldingEnricher;
    private final CommodityHoldingEnricher commodityHoldingEnricher;

    public PortfolioHoldingMarketEnricher(AssetPriceQueryService assetPriceQueryService,
                                          RasyonetFundService rasyonetFundService,
                                          CentralIntegrationLogService integrationLogService,
                                          CryptoHoldingEnricher cryptoHoldingEnricher,
                                          BondHoldingEnricher bondHoldingEnricher,
                                          StockHoldingEnricher stockHoldingEnricher,
                                          FutureHoldingEnricher futureHoldingEnricher,
                                          FxHoldingEnricher fxHoldingEnricher,
                                          GoldHoldingEnricher goldHoldingEnricher,
                                          CommodityHoldingEnricher commodityHoldingEnricher) {
        this.assetPriceQueryService = assetPriceQueryService;
        this.rasyonetFundService = rasyonetFundService;
        this.integrationLogService = integrationLogService;
        this.cryptoHoldingEnricher = cryptoHoldingEnricher;
        this.bondHoldingEnricher = bondHoldingEnricher;
        this.stockHoldingEnricher = stockHoldingEnricher;
        this.futureHoldingEnricher = futureHoldingEnricher;
        this.fxHoldingEnricher = fxHoldingEnricher;
        this.goldHoldingEnricher = goldHoldingEnricher;
        this.commodityHoldingEnricher = commodityHoldingEnricher;
    }

    @Override
    @WithSpan("PortfolioEnrich.holding")
    public void enrich(PortfolioHoldingResponse holding) {
        try {
            AssetType type = holding.getAssetType();
            Span.current().setAttribute("holding.asset_type", type != null ? type.name() : "null");
            Span.current().setAttribute("holding.symbol", String.valueOf(holding.getSymbol()));
            if (type == AssetType.STOCK) {
                stockHoldingEnricher.enrich(holding);
            } else if (type == AssetType.FUTURE) {
                futureHoldingEnricher.enrich(holding);
            } else if (type == AssetType.CRYPTO) {
                cryptoHoldingEnricher.enrich(holding);
            } else if (type == AssetType.GOLD) {
                goldHoldingEnricher.enrich(holding);
            } else if (type == AssetType.COMMODITY) {
                commodityHoldingEnricher.enrich(holding);
            } else if (type == AssetType.FUND) {
                enrichFundHolding(holding);
            } else if (type == AssetType.BOND) {
                bondHoldingEnricher.enrich(holding);
            } else if (type == AssetType.FX) {
                fxHoldingEnricher.enrich(holding);
            } else {
                // Diğer / fallback
                enrichFromPriceSnapshot(holding);
            }
        } catch (UnsupportedOperationException ex) {
            log.debug("Live price not supported for assetType={} symbol={}",
                    holding.getAssetType(), holding.getSymbol());
        } catch (Exception ex) {
            log.warn("Failed to fetch live price for assetType={} symbol={}: {}",
                    holding.getAssetType(), holding.getSymbol(), ex.getMessage());
            publishDegradedMarketFetch(holding.getAssetType(), holding.getSymbol());
        }
    }

    private void publishDegradedMarketFetch(AssetType assetType, String symbol) {
        String provider = resolveProviderForAssetType(assetType);
        integrationLogService.publish(
                IntegrationLogSupport.EVENT_MARKET_DATA_FETCH_FAILED,
                "WARN",
                "Portfolio holding live price enrichment failed",
                provider,
                "portfolio_enrich",
                null,
                null,
                null,
                true,
                Map.of(
                        "assetType", assetType != null ? assetType.name() : "UNKNOWN",
                        "symbol", symbol != null ? symbol : "",
                        "degraded", true,
                        "trigger", IntegrationLogSupport.TRIGGER_API_REQUEST
                ),
                PortfolioHoldingMarketEnricher.class.getName()
        );
    }

    private static String resolveProviderForAssetType(AssetType assetType) {
        if (assetType == null) {
            return IntegrationLogSupport.PROVIDER_EXTERNAL;
        }
        return switch (assetType) {
            case STOCK, COMMODITY -> IntegrationLogSupport.PROVIDER_YAHOO;
            case CRYPTO -> IntegrationLogSupport.PROVIDER_COINGECKO;
            case FUND -> IntegrationLogSupport.PROVIDER_RASYONET;
            case FUTURE -> IntegrationLogSupport.PROVIDER_AKBANK_VIOP;
            default -> IntegrationLogSupport.PROVIDER_EXTERNAL;
        };
    }

    private void applyMasFromCloses(PortfolioHoldingResponse holding, List<BigDecimal> closes) {
        if (closes == null || closes.isEmpty()) {
            return;
        }
        BigDecimal ma20 = PortfolioMovingAverage.simpleMa(closes, 20);
        BigDecimal ma50 = PortfolioMovingAverage.simpleMa(closes, 50);
        if (ma20 != null) {
            holding.setMa20(ma20);
        }
        if (ma50 != null) {
            holding.setMa50(ma50);
        }
    }

    /** FX, BOND gibi basit fiyat snapshot yeterli olan tipler için. */
    private void enrichFromPriceSnapshot(PortfolioHoldingResponse holding) {
        AssetPriceSnapshot snapshot = assetPriceQueryService.getCurrentPrice(
                holding.getAssetType(), holding.getSymbol());

        BigDecimal currentPrice = snapshot.getPrice();
        BigDecimal marketValue  = currentPrice.multiply(holding.getTotalQuantity())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal profitLoss   = marketValue.subtract(holding.getTotalCost())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(currentPrice);
        holding.setMarketValue(marketValue);
        holding.setProfitLoss(profitLoss);
        holding.setCurrency(snapshot.getCurrency());
        holding.setAsOf(snapshot.getAsOf());
    }


    /**
     * FUND: Rasyonet kart / liste — NAV, getiriler, günlük değişim alanları, ~1 yıl NAV geçmişi (52w / MA).
     */
    private void enrichFundHolding(PortfolioHoldingResponse holding) {
        String code = holding.getSymbol().trim().toUpperCase();

        RasyonetFundDto listed = RasyonetFundLookup.findByCode(rasyonetFundService, code);
        List<String> sources = new ArrayList<>();
        if (listed != null && listed.getSourceCode() != null && !listed.getSourceCode().isBlank()) {
            sources.add(listed.getSourceCode().trim().toUpperCase());
        }
        for (String sc : List.of("TMF", "TPF", "TAF")) {
            if (!sources.contains(sc)) sources.add(sc);
        }

        for (String sc : sources) {
            RasyonetFundDetailDto d = rasyonetFundService.getFundDetailRich(code, sc);
            if (d != null && d.getPrice() != null && d.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                applyFundRichDetailToHolding(holding, d);
                return;
            }
        }

        if (listed != null && listed.getPrice() != null
                && listed.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            applyFundRichDetailFromListDto(holding, listed);
            return;
        }

        throw new IllegalArgumentException("Fund price not found for code: " + code);
    }

    /**
     * FUND: Rasyonet zengin kart — NAV, getiriler, günlük % → {@code changePercent},
     * pay başı günlük TL farkı → {@code change} (HoldingsTable günlük TL = qty × change),
     * son ~1 yıl NAV geçmişinden 52 hafta aralığı + MA20/MA50 (trend yedek).
     */
    private void applyFundRichDetailToHolding(PortfolioHoldingResponse holding, RasyonetFundDetailDto d) {
        BigDecimal price = d.getPrice();
        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(FUND_MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(FUND_MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        String cur = d.getCurrencyCode();
        String safeCurrency = isValidIsoCurrency(cur) ? cur : "TRY";
        holding.setCurrency(safeCurrency);
        holding.setAsOf(LocalDateTime.now());
        if (d.getName() != null && !d.getName().isBlank()) {
            holding.setName(d.getName());
        }
        holding.setReturnOneDay(d.getReturnOneDay());
        holding.setReturnOneMonth(d.getReturnOneMonth());
        holding.setReturnThreeMonths(d.getReturnThreeMonths());

        mapFundDailyChangeToHolding(holding, price, d.getReturnOneDay());
        applyFundNavHistoryStats(holding, d.getPriceHistory(), price);
    }

    /** Liste endpoint'inden gelen fiyat; fiyat geçmişi yok → günlük %/TL yine map edilir, 52w/MA boş kalabilir. */
    private void applyFundRichDetailFromListDto(PortfolioHoldingResponse holding, RasyonetFundDto listed) {
        RasyonetFundDetailDto d = new RasyonetFundDetailDto();
        d.setPrice(listed.getPrice());
        d.setCurrencyCode("TRY");
        d.setName(listed.getName());
        d.setReturnOneDay(listed.getReturnOneDay());
        d.setReturnOneMonth(listed.getReturnOneMonth());
        d.setReturnThreeMonths(listed.getReturnThreeMonths());
        d.setPriceHistory(null);
        applyFundRichDetailToHolding(holding, d);
    }

    /** returnOneDay (%) → changePercent; pay başı günlük TL → change. */
    private void mapFundDailyChangeToHolding(PortfolioHoldingResponse holding,
                                             BigDecimal price,
                                             BigDecimal returnOneDayPct) {
        if (returnOneDayPct == null) {
            holding.setChange(null);
            holding.setChangePercent(null);
            return;
        }
        holding.setChangePercent(returnOneDayPct);
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal delta = price.multiply(returnOneDayPct)
                    .divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
            holding.setChange(delta);
        } else {
            holding.setChange(null);
        }
    }

    /**
     * Rasyonet {@code LastYearReturnPrice} serisinden min/max NAV ve kapanış serisi MA20/MA50.
     */
    private void applyFundNavHistoryStats(PortfolioHoldingResponse holding,
                                          List<RasyonetFundDetailDto.PricePoint> rawPh,
                                          BigDecimal currentNav) {
        if (rawPh == null || rawPh.isEmpty()) {
            return;
        }
        List<RasyonetFundDetailDto.PricePoint> ph = new ArrayList<>(rawPh);
        ph.sort(Comparator.comparing(RasyonetFundDetailDto.PricePoint::getDate,
                Comparator.nullsLast(String::compareTo)));

        List<BigDecimal> closes = ph.stream()
                .map(RasyonetFundDetailDto.PricePoint::getPrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (closes.isEmpty()) {
            return;
        }

        BigDecimal hi = closes.stream().max(BigDecimal::compareTo).orElse(null);
        BigDecimal lo = closes.stream().min(BigDecimal::compareTo).orElse(null);
        if (currentNav != null) {
            hi = hi == null ? currentNav : hi.max(currentNav);
            lo = lo == null ? currentNav : lo.min(currentNav);
        }
        holding.setFiftyTwoWeekHigh(hi);
        holding.setFiftyTwoWeekLow(lo);

        List<BigDecimal> forMa = new ArrayList<>(closes);
        if (currentNav != null
                && (forMa.isEmpty() || forMa.get(forMa.size() - 1).compareTo(currentNav) != 0)) {
            forMa.add(currentNav);
        }
        holding.setMa20(PortfolioMovingAverage.simpleMa(forMa, 20));
        holding.setMa50(PortfolioMovingAverage.simpleMa(forMa, 50));
    }

    /**
     * Verilen string'in bilinen ISO 4217 para birimi kodu olup olmadığını kontrol eder.
     * Rasyonet'in kaynak kodları (TMF, TPF, TAF) gibi değerleri filtreler.
     */
    private static boolean isValidIsoCurrency(String code) {
        if (code == null || code.isBlank()) return false;
        try {
            java.util.Currency.getInstance(code.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
