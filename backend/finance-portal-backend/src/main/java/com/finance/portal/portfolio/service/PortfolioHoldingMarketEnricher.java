package com.finance.portal.portfolio.service;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockDetail;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.enrich.CryptoHoldingEnricher;
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

    private static final BigDecimal GOLD_FINENESS_22K = new BigDecimal("0.9166");
    private static final BigDecimal GOLD_FINENESS_14K = new BigDecimal("0.5850");
    private static final BigDecimal GOLD_GROSS_QUARTER  = new BigDecimal("1.754");
    private static final BigDecimal GOLD_GROSS_HALF     = new BigDecimal("3.508");
    private static final BigDecimal GOLD_GROSS_ZIYNET   = new BigDecimal("7.016");
    private static final BigDecimal GOLD_GROSS_REPUBLIC = new BigDecimal("7.216");

    private final AssetPriceQueryService assetPriceQueryService;
    private final GoldMarketService goldMarketService;
    private final YahooCommodityService yahooCommodityService;
    private final SilverMarketService silverMarketService;
    private final EvdsBondService evdsBondService;
    private final StockQueryService stockQueryService;
    private final MarketFxService marketFxService;
    private final RasyonetFundService rasyonetFundService;
    private final ViopService viopService;
    private final ViopChartService viopChartService;
    private final CentralIntegrationLogService integrationLogService;
    private final EurobondService eurobondService;
    private final CryptoHoldingEnricher cryptoHoldingEnricher;

    public PortfolioHoldingMarketEnricher(AssetPriceQueryService assetPriceQueryService,
                                          GoldMarketService goldMarketService,
                                          YahooCommodityService yahooCommodityService,
                                          SilverMarketService silverMarketService,
                                          EvdsBondService evdsBondService,
                                          StockQueryService stockQueryService,
                                          MarketFxService marketFxService,
                                          RasyonetFundService rasyonetFundService,
                                          ViopService viopService,
                                          ViopChartService viopChartService,
                                          CentralIntegrationLogService integrationLogService,
                                          EurobondService eurobondService,
                                          CryptoHoldingEnricher cryptoHoldingEnricher) {
        this.assetPriceQueryService = assetPriceQueryService;
        this.goldMarketService = goldMarketService;
        this.yahooCommodityService = yahooCommodityService;
        this.silverMarketService = silverMarketService;
        this.evdsBondService = evdsBondService;
        this.stockQueryService = stockQueryService;
        this.marketFxService = marketFxService;
        this.rasyonetFundService = rasyonetFundService;
        this.viopService = viopService;
        this.viopChartService = viopChartService;
        this.integrationLogService = integrationLogService;
        this.eurobondService = eurobondService;
        this.cryptoHoldingEnricher = cryptoHoldingEnricher;
    }
    private static String goldHoldingDisplayName(String upper) {
        return switch (upper) {
            case "GOLD" -> "Altın (Ons)";
            case "GRAM" -> "Gram Altın";
            case "14AYAR", "AYAR14" -> "14 Ayar Bilezik";
            case "22AYAR", "AYAR22" -> "22 Ayar Bilezik";
            case "CEYREK" -> "Çeyrek Altın";
            case "YARIM" -> "Yarım Altın";
            case "TAM", "ZIYNET" -> "Tam Altın";
            case "CUMHUR", "ATA" -> "Cumhuriyet Altını";
            default -> "Altın (" + upper + ")";
        };
    }

    private void enrichBondHolding(PortfolioHoldingResponse holding) {
        String code = holding.getSymbol() != null ? holding.getSymbol().trim() : "";
        EvdsBondInstrument bond = evdsBondService.getEvdsBondDetail(code);
        BigDecimal price = bond.getIndicatorValue();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Bond EVDS indicator unavailable for: " + code);
        }

        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setChange(bond.getDailyChange());
        holding.setChangePercent(bond.getDailyChangePercent());

        LocalDate lu = bond.getLastUpdated();
        holding.setAsOf(lu != null ? lu.atStartOfDay() : LocalDateTime.now());

        if (bond.getType() != null && !bond.getType().isBlank()) {
            holding.setName(code + " · " + bond.getType());
        }

        try {
            List<EvdsBondHistoryPoint> hist = evdsBondService.getEvdsBondHistory(code, BondPeriod.ONE_YEAR);
            if (hist != null && !hist.isEmpty()) {
                List<BigDecimal> closes = hist.stream()
                        .map(EvdsBondHistoryPoint::getIndicatorValue)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (!closes.isEmpty()) {
                    holding.setFiftyTwoWeekHigh(closes.stream().max(BigDecimal::compareTo).orElse(null));
                    holding.setFiftyTwoWeekLow(closes.stream().min(BigDecimal::compareTo).orElse(null));
                    holding.setMa20(PortfolioMovingAverage.simpleMa(closes, 20));
                    holding.setMa50(PortfolioMovingAverage.simpleMa(closes, 50));
                }
            }
        } catch (Exception e) {
            log.debug("Bond history / MA unavailable for {}: {}", code, e.getMessage());
        }
    }

    /**
     * Eurobond (Hazine dış borç) holding'i — TL hesaplama (Model 1: TL maliyet, canlı kur).
     * Fiyat/künye Business Insider'dan; kote (USD/EUR/JPY) canlı TCMB satış kuruyla TL'ye çevrilir.
     * Maliyet kullanıcı tarafından TL girildiği için K/Z hem tahvil hem kur hareketini içerir.
     * 52w/MA serisi de aynı (güncel) kurla TL'ye çevrilir (altın/emtia ile aynı yaklaşım).
     */
    private void enrichEurobondHolding(PortfolioHoldingResponse holding, String isin) {
        EurobondDetail d = eurobondService.detail(isin);
        if (d == null || d.getLastPriceTry() == null) {
            holding.setCurrency("TRY");
            holding.setName(d != null && d.getName() != null ? d.getName() : isin);
            return;
        }
        BigDecimal priceTry = d.getLastPriceTry();
        BigDecimal qty = holding.getTotalQuantity() != null ? holding.getTotalQuantity() : BigDecimal.ZERO;
        BigDecimal mv = priceTry.multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal cost = holding.getTotalCost() != null ? holding.getTotalCost() : BigDecimal.ZERO;
        BigDecimal pl = mv.subtract(cost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(priceTry);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setName(d.getName() != null ? d.getName() : isin);
        holding.setChangePercent(d.getChangePercent());
        holding.setAsOf(LocalDateTime.now());

        try {
            BigDecimal rate = d.getFxRate() != null ? d.getFxRate() : BigDecimal.ONE;
            List<BigDecimal> closes = eurobondService.chart(isin, "1Y").stream()
                    .map(EurobondChartPoint::close).filter(Objects::nonNull)
                    .map(c -> c.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                    .collect(Collectors.toList());
            if (!closes.isEmpty()) {
                holding.setFiftyTwoWeekHigh(closes.stream().max(BigDecimal::compareTo).orElse(null));
                holding.setFiftyTwoWeekLow(closes.stream().min(BigDecimal::compareTo).orElse(null));
                applyMasFromCloses(holding, closes);
            }
        } catch (Exception e) {
            log.debug("Eurobond 52w/MA alınamadı {}: {}", isin, e.getMessage());
        }
    }

    @Override
    @WithSpan("PortfolioEnrich.holding")
    public void enrich(PortfolioHoldingResponse holding) {
        try {
            AssetType type = holding.getAssetType();
            Span.current().setAttribute("holding.asset_type", type != null ? type.name() : "null");
            Span.current().setAttribute("holding.symbol", String.valueOf(holding.getSymbol()));
            if (type == AssetType.STOCK) {
                enrichStockOrFutureHolding(holding);
            } else if (type == AssetType.FUTURE) {
                enrichFutureHolding(holding);
            } else if (type == AssetType.CRYPTO) {
                cryptoHoldingEnricher.enrich(holding);
            } else if (type == AssetType.GOLD) {
                enrichGoldHolding(holding);
            } else if (type == AssetType.COMMODITY) {
                enrichCommodityHolding(holding);
            } else if (type == AssetType.FUND) {
                enrichFundHolding(holding);
            } else if (type == AssetType.BOND) {
                String code = holding.getSymbol() != null ? holding.getSymbol().trim().toUpperCase() : "";
                if (eurobondService.currentIsins().contains(code)) {
                    enrichEurobondHolding(holding, code);
                } else {
                    enrichBondHolding(holding);
                }
            } else if (type == AssetType.FX) {
                enrichFxHolding(holding);
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

    /**
     * STOCK: StockDetail üzerinden zenginleştirilmiş holding verisi.
     * Günlük değişim, hacim, intraday high/low ve 52 hafta aralığı doldurulur.
     * getStockDetail @Cacheable ile önbelleğe alındığı için tekrar API çağrısı yapılmaz.
     * MA20/MA50, 3 aylık günlük chart verisi üzerinden hesaplanır (önbellek: market.stocks.chart).
     */
    private void enrichStockOrFutureHolding(PortfolioHoldingResponse holding) {
        StockDetail detail = stockQueryService.getStockDetail(holding.getSymbol().toUpperCase());
        StockSummary summary = detail.getSummary();

        BigDecimal price = summary.getPrice();
        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency(summary.getCurrency() != null ? summary.getCurrency() : detail.getCurrency());
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(summary.getAsOf()));
        holding.setName(detail.getName() != null ? detail.getName()
                : (summary.getName() != null ? summary.getName() : holding.getSymbol()));
        holding.setChange(summary.getChange());
        holding.setChangePercent(summary.getChangePercent());
        holding.setVolume(summary.getVolume());
        holding.setDayHigh(summary.getDayHigh());
        holding.setDayLow(summary.getDayLow());
        holding.setFiftyTwoWeekHigh(detail.getFiftyTwoWeekHigh());
        holding.setFiftyTwoWeekLow(detail.getFiftyTwoWeekLow());

        // MA20 / MA50 — 3 aylık günlük kapanışlardan hesapla (önbellekte ise bedava)
        try {
            StockChartResponse chart = stockQueryService.getStockChartWithParams(
                    holding.getSymbol().toUpperCase(), "3mo", "1d");
            List<BigDecimal> closes = chart.getClosePrices();
            holding.setMa20(PortfolioMovingAverage.simpleMa(closes, 20));
            holding.setMa50(PortfolioMovingAverage.simpleMa(closes, 50));
        } catch (Exception e) {
            log.debug("MA computation skipped for {}: {}", holding.getSymbol(), e.getMessage());
        }
    }

    /**
     * FUTURE (VİOP): İş Yatırım grafiği ile 52w/MA her zaman denenir; Akbank listesi ile anlık fiyat vb. eklenir.
     * <p>
     * Akbank {@code market.viop.contracts} önbelleği geçici boş / eşleşme başarısız olsa bile erken {@code return}
     * grafik adımını atlamasın diye grafik zenginleştirmesi liste yolundan ayrılmıştır.
     * <ul>
     *   <li>Mevcut fiyat: lastPrice → settlementPrice</li>
     *   <li>Piyasa değeri: kalan miktar × fiyat × çarpan (çarpan bilgisi yoksa 1)</li>
     *   <li>{@code change}: kontrat başına günlük fark (current − önceki uzlaşma — frontend günlük K/Z için qty ile çarpıyor)</li>
     *   <li>{@code changePercent}: listedeki günlük %</li>
     *   <li>{@code volume}: toplam açık pozisyon (open interest)</li>
     *   <li>İş Yatırım grafik (1Y→…): min/max → 52 hafta kolonları; günlük son kapanışlar → MA20/MA50</li>
     * </ul>
     */
    private void enrichFutureHolding(PortfolioHoldingResponse holding) {
        String contractName = holding.getSymbol() != null ? holding.getSymbol().trim() : null;
        if (contractName == null || contractName.isBlank()) {
            return;
        }
        // Liste başarısız olsa bile (geçici boş cache vb.) 52w / MA kaybolmasın
        applyViopYearChartMetrics(holding, contractName);

        final ViopContractDetail d;
        try {
            Optional<ViopContract> match = viopService.findMatchingContract(contractName);
            if (match.isEmpty()) {
                log.debug("VIOP contracts list had no row matching holding symbol={}", contractName);
                return;
            }
            d = viopService.buildDetailDto(match.get());
        } catch (Exception ex) {
            log.warn("VIOP enrichment failed for holding symbol={}: {}",
                    contractName, ex.getMessage());
            return;
        }

        BigDecimal current = d.getLastPrice();
        if (current == null) {
            current = d.getSettlementPrice();
        }
        if (current == null) {
            log.debug("VIOP no price for contract {}", contractName);
            return;
        }

        BigDecimal multiplier = BigDecimal.ONE;

        BigDecimal qty = holding.getTotalQuantity() != null ? holding.getTotalQuantity() : BigDecimal.ZERO;
        BigDecimal mv = qty.multiply(current).multiply(multiplier).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalCost = holding.getTotalCost() != null ? holding.getTotalCost() : BigDecimal.ZERO;
        BigDecimal pl = mv.subtract(totalCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(current);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setName(d.getName() != null ? d.getName() : contractName);
        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(d.getTime());
        holding.setAsOf(asOf != null ? asOf : LocalDateTime.now());

        holding.setChangePercent(d.getChangePercent());
        holding.setDayHigh(d.getHigh());
        holding.setDayLow(d.getLow());

        BigDecimal prevSet = d.getPrevSettlementPrice();
        if (prevSet != null) {
            holding.setChange(current.subtract(prevSet).multiply(multiplier)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }

        // VİOP'ta gerçek işlem hacmi verisi yok (yalnız açık pozisyon var) → "Hacim" doldurulmaz.

        String canonical = d.getName() != null ? d.getName() : contractName;
        if (!canonical.trim().equals(contractName)) {
            applyViopYearChartMetrics(holding, canonical);
        }
    }

    /**
     * İş Yatırım grafik serisinden (1Y → 6A → 3A → 1A, ilk dolu seri) min/max aralığı ve
     * günlük son kapanışlardan MA20/MA50 hesaplar.
     */
    private void applyViopYearChartMetrics(PortfolioHoldingResponse holding, String chartContractName) {
        if (chartContractName == null || chartContractName.isBlank()) {
            return;
        }
        String trimmed = chartContractName.trim();
        List<ViopChartPoint> pts = null;
        try {
            for (ViopChartPeriod p : List.of(
                    ViopChartPeriod.ONE_YEAR,
                    ViopChartPeriod.SIX_MONTHS,
                    ViopChartPeriod.THREE_MONTHS,
                    ViopChartPeriod.ONE_MONTH)) {
                List<ViopChartPoint> chunk = viopChartService.getChart(trimmed, p);
                if (chunk != null && chunk.size() >= 2) {
                    pts = chunk;
                    break;
                }
            }
        } catch (UnsupportedViopContractException ex) {
            log.debug("VIOP chart unsupported for '{}': {}", trimmed, ex.getMessage());
            return;
        } catch (Exception ex) {
            log.debug("VIOP chart fetch failed for '{}': {}", trimmed, ex.getMessage());
            return;
        }
        if (pts == null || pts.isEmpty()) {
            return;
        }
        List<ViopChartPoint> sorted = new ArrayList<>(pts);
        sorted.sort(Comparator.comparing(ViopChartPoint::getTimestamp, Comparator.nullsLast(Long::compareTo)));

        List<BigDecimal> allVals = new ArrayList<>();
        TreeMap<LocalDate, BigDecimal> dailyLast = new TreeMap<>();
        for (ViopChartPoint p : sorted) {
            if (p.getValue() == null) {
                continue;
            }
            allVals.add(p.getValue());
            LocalDate day = chartPointToLocalDate(p);
            if (day != null) {
                dailyLast.put(day, p.getValue());
            }
        }
        if (allVals.isEmpty()) {
            return;
        }
        BigDecimal lo = allVals.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal hi = allVals.stream().max(BigDecimal::compareTo).orElse(null);
        if (lo != null && hi != null) {
            holding.setFiftyTwoWeekLow(lo.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
            holding.setFiftyTwoWeekHigh(hi.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
        }

        List<BigDecimal> dailyCloses = new ArrayList<>(dailyLast.values());
        BigDecimal ma20 = PortfolioMovingAverage.simpleMa(dailyCloses, 20);
        BigDecimal ma50 = PortfolioMovingAverage.simpleMa(dailyCloses, 50);
        if (ma20 != null) {
            holding.setMa20(ma20);
        }
        if (ma50 != null) {
            holding.setMa50(ma50);
        }
    }

    private static LocalDate chartPointToLocalDate(ViopChartPoint p) {
        String dt = p.getDateTime();
        if (dt == null || dt.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(dt.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * FX: TCMB son kur + 1Y tarihsel veri ile zenginleştirme.
     *  - Anlık fiyat: TCMB satış kuru (kullanıcı perspektifi)
     *  - Günlük change/changePercent: son iki kapanış farkı
     *  - 52w high/low: 1Y close listesinden min/max
     *  - 1M / 3M getiri: yaklaşık 22/66 işlem günü öncesi close ile karşılaştırma (trend için)
     *  - MA20/MA50: son N kapanış üzerinden basit hareketli ortalama
     */
    private void enrichFxHolding(PortfolioHoldingResponse holding) {
        String symbol = holding.getSymbol() != null ? holding.getSymbol().toUpperCase() : "";

        // 1) Anlık kur — kullanıcı perspektifinde satış kuru (BUY referansı)
        FxLatestRates latest = marketFxService.getTcmbLatestRates(symbol);
        FxRateItem rate = latest.getRates().stream()
                .filter(r -> symbol.equalsIgnoreCase(r.getSymbol()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("FX rate not found for: " + symbol));

        int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
        BigDecimal unitBd = BigDecimal.valueOf(unit);
        BigDecimal currentPrice = rate.getSell();
        if (currentPrice == null) currentPrice = rate.getBuy();
        if (currentPrice == null) {
            throw new IllegalStateException("FX price unavailable for: " + symbol);
        }
        if (unit > 1) {
            currentPrice = currentPrice.divide(unitBd, 6, RoundingMode.HALF_UP);
        }

        BigDecimal mv = currentPrice.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(currentPrice);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(latest.getAsOf()));
        holding.setName(symbol + "/TRY");

        // 2) Tarihsel veriden günlük değişim, 52w aralığı, dönemsel getiriler, MA
        try {
            FxHistory hist = marketFxService.getFxHistory(symbol, "1Y");
            List<FxHistoryPoint> pts = hist != null ? hist.getPoints() : null;
            if (pts != null && !pts.isEmpty()) {
                // Kapanışları kronolojik sırala
                List<BigDecimal> closes = pts.stream()
                        .filter(p -> p.getClose() != null)
                        .sorted(Comparator.comparing(FxHistoryPoint::getDate,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(p -> {
                            BigDecimal c = p.getClose();
                            return unit > 1 ? c.divide(unitBd, 6, RoundingMode.HALF_UP) : c;
                        })
                        .collect(java.util.stream.Collectors.toList());

                int n = closes.size();
                if (n >= 2) {
                    BigDecimal last = closes.get(n - 1);
                    BigDecimal prev = closes.get(n - 2);
                    BigDecimal change = last.subtract(prev);
                    holding.setChange(change.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                    if (prev.compareTo(BigDecimal.ZERO) != 0) {
                        holding.setChangePercent(change
                                .divide(prev, 8, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP));
                    }
                }

                // 52w aralığı
                holding.setFiftyTwoWeekHigh(closes.stream().max(BigDecimal::compareTo).orElse(null));
                holding.setFiftyTwoWeekLow(closes.stream().min(BigDecimal::compareTo).orElse(null));

                // 1A / 3A getiri (yaklaşık 22 / 66 işlem günü)
                BigDecimal latestClose = closes.get(n - 1);
                holding.setReturnOneMonth(periodReturnPercent(closes, latestClose, 22));
                holding.setReturnThreeMonths(periodReturnPercent(closes, latestClose, 66));

                // MA20 / MA50 — basit hareketli ortalama
                holding.setMa20(PortfolioMovingAverage.simpleMa(closes, 20));
                holding.setMa50(PortfolioMovingAverage.simpleMa(closes, 50));
            }
        } catch (Exception e) {
            log.debug("FX history enrichment skipped for {}: {}", symbol, e.getMessage());
        }
    }

    /**
     * Dizinin son elemanına göre {@code daysBack} işlem günü öncesinin yüzdesel değişimini hesaplar.
     * Yeterli veri yoksa null döner.
     */
    private BigDecimal periodReturnPercent(List<BigDecimal> closes, BigDecimal latest, int daysBack) {
        if (closes == null || latest == null) return null;
        int n = closes.size();
        if (n <= daysBack) return null;
        BigDecimal past = closes.get(n - 1 - daysBack);
        if (past == null || past.compareTo(BigDecimal.ZERO) == 0) return null;
        return latest.subtract(past)
                .divide(past, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
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

    private BigDecimal fetchUsdTryRate() {
        try {
            FxLatestRates latest = marketFxService.getTcmbLatestRates("USD");
            if (latest == null || latest.getRates() == null) {
                return null;
            }
            return latest.getRates().stream()
                    .filter(r -> "USD".equalsIgnoreCase(r.getSymbol()))
                    .map(FxRateItem::getSell)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("USD/TRY rate unavailable: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Altın sembolüne göre 1Y kapanış serisi (mevcut fiyat birimiyle uyumlu).
     * GOLD → TRY/ons; GRAM ve türevleri → gram TRY veya teorik çarpan.
     */
    private List<BigDecimal> buildGoldPriceSeries(String upper, BigDecimal usdTry) {
        if ("GOLD".equals(upper)) {
            if (usdTry == null) {
                return List.of();
            }
            GoldHistoryResponse hist = goldMarketService.getGoldHistory("1Y", "USD");
            if (hist == null || hist.getPoints() == null) {
                return List.of();
            }
            return hist.getPoints().stream()
                    .map(GoldHistoryPoint::getClose)
                    .filter(Objects::nonNull)
                    .map(c -> c.multiply(usdTry).setScale(2, RoundingMode.HALF_UP))
                    .collect(Collectors.toList());
        }
        GoldHistoryResponse hist = goldMarketService.getGoldHistory("1Y", "TRY");
        if (hist == null || hist.getPoints() == null) {
            return List.of();
        }
        List<BigDecimal> gramCloses = hist.getPoints().stream()
                .map(GoldHistoryPoint::getClose)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        BigDecimal factor = goldTheoryFactor(upper);
        if (factor == null) {
            return gramCloses;
        }
        return gramCloses.stream()
                .map(g -> g.multiply(factor).setScale(2, RoundingMode.HALF_UP))
                .collect(Collectors.toList());
    }

    private static BigDecimal goldTheoryFactor(String upper) {
        return switch (upper) {
            case "GRAM" -> null;
            case "14AYAR", "AYAR14" -> GOLD_FINENESS_14K;
            case "22AYAR", "AYAR22" -> GOLD_FINENESS_22K;
            case "CEYREK" -> GOLD_GROSS_QUARTER.multiply(GOLD_FINENESS_22K);
            case "YARIM" -> GOLD_GROSS_HALF.multiply(GOLD_FINENESS_22K);
            case "TAM", "ZIYNET" -> GOLD_GROSS_ZIYNET.multiply(GOLD_FINENESS_22K);
            case "CUMHUR", "ATA" -> GOLD_GROSS_REPUBLIC.multiply(GOLD_FINENESS_22K);
            default -> null;
        };
    }

    private void applyGoldHistoryMetrics(PortfolioHoldingResponse holding, String upper, BigDecimal usdTry) {
        try {
            List<BigDecimal> series = buildGoldPriceSeries(upper, usdTry);
            if (series.isEmpty()) {
                return;
            }
            holding.setFiftyTwoWeekHigh(series.stream().max(BigDecimal::compareTo).orElse(null));
            holding.setFiftyTwoWeekLow(series.stream().min(BigDecimal::compareTo).orElse(null));
            applyMasFromCloses(holding, series);
        } catch (Exception e) {
            log.debug("Gold history metrics unavailable for {}: {}", upper, e.getMessage());
        }
    }

    private void applyYahooCommodityMas(PortfolioHoldingResponse holding, String symbol) {
        try {
            CommodityHistoryResponse hist = yahooCommodityService.getHistory(symbol, "1Y", "1d");
            if (hist == null || hist.getPoints() == null || hist.getPoints().isEmpty()) {
                return;
            }
            BigDecimal usdTry = "TRY".equalsIgnoreCase(holding.getCurrency()) ? fetchUsdTryRate() : null;
            List<BigDecimal> closes = hist.getPoints().stream()
                    .map(CommodityHistoryPointDto::getDisplayClose)
                    .filter(Objects::nonNull)
                    .map(c -> usdTry != null ? c.multiply(usdTry).setScale(2, RoundingMode.HALF_UP) : c)
                    .collect(Collectors.toList());
            applyMasFromCloses(holding, closes);
        } catch (Exception e) {
            log.debug("Commodity MA skipped for {}: {}", symbol, e.getMessage());
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
     * GOLD: GoldMarketService üzerinden spot fiyat + ons değişim verileri.
     * GOLD sembolü → TRY/ons (onsUsd × TCMB).
     * GRAM sembolü → TRY/gram fiyatı.
     */
    private void enrichGoldHolding(PortfolioHoldingResponse holding) {
        GoldSpotResponse spot = goldMarketService.getSpotGold();
        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(spot.getLastUpdated());
        if (asOf == null) asOf = PortfolioDateTimeParse.parseLenient(spot.getUpdatedAt());

        String upper = holding.getSymbol().toUpperCase();
        BigDecimal price;
        String currency;
        BigDecimal change = null;
        BigDecimal changePercent = null;
        BigDecimal high = null;
        BigDecimal low  = null;

        switch (upper) {
            case "GOLD" -> {
                // İşlem fiyatı TRY/ons → canlı fiyat da TRY/ons (ham USD/ons kullanılmaz)
                price = spot.getOnsTry();
                if (price == null && spot.getOnsUsd() != null && spot.getUsdTry() != null) {
                    price = spot.getOnsUsd().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP);
                }
                currency = "TRY";
                BigDecimal usdTry = spot.getUsdTry();
                if (spot.getOnsChange() != null && usdTry != null) {
                    change = spot.getOnsChange().multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
                }
                changePercent = spot.getOnsChangePercent();
                if (spot.getOnsHigh() != null && usdTry != null) {
                    high = spot.getOnsHigh().multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
                }
                if (spot.getOnsLow() != null && usdTry != null) {
                    low = spot.getOnsLow().multiply(usdTry).setScale(2, RoundingMode.HALF_UP);
                }
            }
            case "GRAM" -> {
                price    = spot.getGramGoldTry() != null ? spot.getGramGoldTry() : spot.getGramTl();
                currency = "TRY";
                high     = spot.getGramHighTry();
                low      = spot.getGramLowTry();
                change   = spot.getChange();
                changePercent = spot.getChangePercent();
            }
            case "CEYREK" -> { price = spot.getQuarterGoldTry();   currency = "TRY"; }
            case "YARIM"  -> { price = spot.getHalfGoldTry();      currency = "TRY"; }
            case "TAM"    -> { price = spot.getZiynetGoldTry();     currency = "TRY"; }
            case "ZIYNET" -> { price = spot.getZiynetGoldTry();     currency = "TRY"; }
            case "CUMHUR", "ATA" -> { price = spot.getRepublicGoldTry(); currency = "TRY"; }
            case "14AYAR", "AYAR14" -> {
                price = spot.getFourteenKBraceletTry() != null ? spot.getFourteenKBraceletTry() : spot.getAyar14Tl();
                currency = "TRY";
                change = spot.getChange();
                changePercent = spot.getChangePercent();
            }
            case "22AYAR", "AYAR22" -> {
                price = spot.getTwentyTwoKBraceletTry() != null ? spot.getTwentyTwoKBraceletTry() : spot.getAyar22Tl();
                currency = "TRY";
                change = spot.getChange();
                changePercent = spot.getChangePercent();
            }
            default -> throw new UnsupportedOperationException("Unsupported gold symbol: " + upper);
        }

        if (price == null) {
            if ("GOLD".equals(upper)) {
                holding.setCurrency("TRY");
                holding.setName("Altın (Ons)");
                holding.setAsOf(asOf);
                if (spot.getQuantityKg() != null) {
                    holding.setVolume(spot.getQuantityKg().longValue());
                }
                return;
            }
            throw new IllegalStateException("Gold price unavailable for: " + upper);
        }

        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // Sikke/ziynet (Çeyrek/Yarım/Tam/Ata) için günlük değişim spot'ta ayrı gelmiyor;
        // gram altınla aynı oranda hareket ettiklerinden gram günlük %'sinden türetilir
        // (değişim tutarı = fiyat × %/100). GRAM/ONS/14-22 ayar zaten kendi değerini taşır.
        if (changePercent == null && spot.getChangePercent() != null) {
            changePercent = spot.getChangePercent();
        }
        if (change == null && changePercent != null) {
            change = price.multiply(changePercent)
                    .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
        }

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency(currency);
        holding.setAsOf(asOf);
        holding.setName(goldHoldingDisplayName(upper));
        holding.setChange(change);
        holding.setChangePercent(changePercent);
        holding.setDayHigh(high);
        holding.setDayLow(low);

        // 52 hafta + MA20/MA50 — trend hesabı için aynı fiyat serisi
        applyGoldHistoryMetrics(holding, upper, spot.getUsdTry());

        // Volume: BIST altın hacmini quantityKg üzerinden doldur (Long'a çevir)
        if (spot.getQuantityKg() != null) {
            holding.setVolume(spot.getQuantityKg().longValue());
        }
    }

    /**
     * COMMODITY: SILVER:GRAM_TRY gibi BIST semboller için SilverMarketService,
     * diğerleri için YahooCommodityService (NG=F, CL=F vb.).
     * CommoditySpotDto zaten change, changePercent, dayHigh/Low, weekHigh52/Low52, volume içeriyor.
     */
    private void enrichCommodityHolding(PortfolioHoldingResponse holding) {
        String symbol = holding.getSymbol();

        if (symbol.contains(":")) {
            String[] parts = symbol.split(":", 2);
            String metal = parts[0].toUpperCase();
            String cat   = parts.length > 1 ? parts[1].toUpperCase() : "";
            if ("SILVER".equals(metal)) {
                enrichSilverHolding(holding, cat);
                return;
            }
        }

        // Yahoo Finance destekli emtia (NG=F, CL=F, GC=F vb.)
        CommoditySpotDto spot = yahooCommodityService.getSpot(symbol);
        BigDecimal price = spot.getDisplayPrice() != null ? spot.getDisplayPrice() : spot.getRawPrice();
        if (price == null) throw new IllegalStateException("Commodity price unavailable: " + symbol);

        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
        String displayName = spot.getDisplayNameTr() != null ? spot.getDisplayNameTr() : spot.getDisplayNameEn();
        if (displayName != null) holding.setName(displayName);
        holding.setChange(spot.getChange());
        holding.setChangePercent(spot.getChangePercent());
        // Emtia için güvenilir işlem hacmi gelmiyor → "Hacim" doldurulmaz.
        holding.setDayHigh(spot.getDayHigh());
        holding.setDayLow(spot.getDayLow());
        holding.setFiftyTwoWeekHigh(spot.getWeekHigh52());
        holding.setFiftyTwoWeekLow(spot.getWeekLow52());
        applyYahooCommodityMas(holding, symbol);
    }

    /** SILVER:GRAM_TRY / SILVER:KG_TRY / SILVER:USD_ONS gibi BIST gümüş sembolleri. */
    private void enrichSilverHolding(PortfolioHoldingResponse holding, String cat) {
        SilverSpotResponse spot = silverMarketService.getSpotSilver();
        BigDecimal price = null;
        String currency = "TRY";
        BigDecimal high = null;
        BigDecimal low  = null;
        BigDecimal change = null;
        BigDecimal changePercent = null;

        switch (cat.isBlank() ? "GRAM_TRY" : cat) {
            case "GRAM_TRY" -> {
                SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "TRY");
                PortfolioHistoryPoints.SilverWindow lp = PortfolioHistoryPoints.silverWindow(hist);
                if (lp.latest() != null && lp.latest().getClose() != null) {
                    price = lp.latest().getClose();
                    high = lp.latest().getHigh();
                    low = lp.latest().getLow();
                } else {
                    price = spot.getSilverGramCloseTry();
                    high = spot.getSilverGramHighTry();
                    low = spot.getSilverGramLowTry();
                }
                if (price == null) {
                    price = spot.getSilverGramTry();
                }
                if (lp.latest() != null && lp.prev() != null && lp.latest().getClose() != null
                        && lp.prev().getClose() != null && lp.prev().getClose().compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal refPrice = price != null ? price : lp.latest().getClose();
                    change = refPrice.subtract(lp.prev().getClose());
                    changePercent = change.divide(lp.prev().getClose(), 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            case "KG_TRY" -> {
                price = spot.getWeightedAverageTryKg();
                high = spot.getHighTryKg();
                low = spot.getLowTryKg();
            }
            case "USD_ONS" -> {
                SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "USD");
                PortfolioHistoryPoints.SilverWindow lp = PortfolioHistoryPoints.silverWindow(hist);
                if (lp.latest() != null) {
                    price = lp.latest().getClose();
                    high = lp.latest().getHigh();
                    low = lp.latest().getLow();
                    if (price != null && lp.prev() != null && lp.prev().getClose() != null
                            && lp.prev().getClose().compareTo(BigDecimal.ZERO) != 0) {
                        change = price.subtract(lp.prev().getClose());
                        changePercent = change.divide(lp.prev().getClose(), 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    price = spot.getSilverUsdOns();
                    currency = "USD";
                }
            }
            default -> throw new UnsupportedOperationException("Unsupported silver category: " + cat);
        }

        if (price == null) throw new IllegalStateException("Silver price unavailable for cat: " + cat);

        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency(currency);
        holding.setAsOf(PortfolioDateTimeParse.parseLenient(spot.getLastUpdated()));
        holding.setName("Gümüş");
        holding.setChange(change);
        holding.setChangePercent(changePercent);
        holding.setDayHigh(high);
        holding.setDayLow(low);

        // 52-week range — 1Y history min/max
        try {
            String histCurrency = "USD".equals(currency) ? "USD" : "TRY";
            SilverHistoryResponse hist1y = silverMarketService.getSilverHistory("1Y", histCurrency);
            if (hist1y != null && hist1y.getPoints() != null) {
                List<BigDecimal> closes1y = hist1y.getPoints().stream()
                        .map(SilverHistoryPoint::getClose)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toList());
                if (!closes1y.isEmpty()) {
                    holding.setFiftyTwoWeekHigh(closes1y.stream().max(BigDecimal::compareTo).orElse(null));
                    holding.setFiftyTwoWeekLow(closes1y.stream().min(BigDecimal::compareTo).orElse(null));
                    applyMasFromCloses(holding, closes1y);
                }
            }
        } catch (Exception e) {
            log.debug("52w range unavailable for SILVER {}: {}", cat, e.getMessage());
        }

        // Emtia (gümüş) için "Hacim" gösterilmez (güvenilir işlem hacmi değil).
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
