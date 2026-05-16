package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.commodity.CommodityHistoryPointDto;
import com.finance.portal.market.application.commodity.CommodityHistoryResponse;
import com.finance.portal.market.application.commodity.CommoditySpotDto;
import com.finance.portal.market.application.commodity.YahooCommodityService;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.gold.GoldHistoryResponse;
import com.finance.portal.market.application.gold.GoldHistoryPoint;
import com.finance.portal.market.application.precious.PreciousMetalService;
import com.finance.portal.market.application.precious.PreciousMetalSpotResponse;
import com.finance.portal.market.application.precious.PreciousMetalHistoryResponse;
import com.finance.portal.market.application.precious.PreciousMetalHistoryPoint;
import com.finance.portal.market.application.silver.SilverMarketService;
import com.finance.portal.market.application.silver.SilverHistoryResponse;
import com.finance.portal.market.application.silver.SilverHistoryPoint;
import com.finance.portal.market.application.silver.SilverSpotResponse;
import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.stock.StockDetail;
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.presentation.dto.ViopChartPointDto;
import com.finance.portal.market.presentation.dto.ViopContractDetailDto;
import com.finance.portal.market.crypto.application.CryptoMarketItem;
import com.finance.portal.market.crypto.application.CryptoMarketService;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.presentation.dto.FxHistoryPoint;
import com.finance.portal.market.presentation.dto.FxHistoryResponse;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import com.finance.portal.market.presentation.dto.FxRateItemDto;
import com.finance.portal.market.infrastructure.external.precious.PreciousMetalType;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.domain.WatchlistItem;
import com.finance.portal.portfolio.dto.AddTransactionRequest;
import com.finance.portal.portfolio.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.dto.PortfolioResponse;
import com.finance.portal.portfolio.dto.PortfolioTransactionResponse;
import com.finance.portal.portfolio.dto.UpdatePortfolioRequest;
import com.finance.portal.portfolio.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.infrastructure.cache.PortfolioRedisCache;
import com.finance.portal.portfolio.repository.PortfolioRepository;
import com.finance.portal.portfolio.repository.PortfolioTransactionRepository;
import com.finance.portal.portfolio.repository.WatchlistItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PortfolioServiceImpl implements PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioServiceImpl.class);
    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    /** FUND: NAV ve maliyet farklarında daha yüksek hassasiyet (hisse/kripto/FX ile karıştırılmaz). */
    private static final int FUND_MONEY_SCALE = 8;

    /** Teorik altın türev fiyatları — GoldMarketService ile aynı çarpanlar (trend MA serisi). */
    private static final BigDecimal GOLD_FINENESS_22K = new BigDecimal("0.9166");
    private static final BigDecimal GOLD_FINENESS_14K = new BigDecimal("0.5850");
    private static final BigDecimal GOLD_GROSS_QUARTER  = new BigDecimal("1.754");
    private static final BigDecimal GOLD_GROSS_HALF     = new BigDecimal("3.508");
    private static final BigDecimal GOLD_GROSS_ZIYNET   = new BigDecimal("7.016");
    private static final BigDecimal GOLD_GROSS_REPUBLIC = new BigDecimal("7.216");

    private final PortfolioRepository portfolioRepository;
    private final PortfolioTransactionRepository portfolioTransactionRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final AssetPriceQueryService assetPriceQueryService;
    private final GoldMarketService goldMarketService;
    private final YahooCommodityService yahooCommodityService;
    private final SilverMarketService silverMarketService;
    private final PreciousMetalService preciousMetalService;
    private final EvdsBondService evdsBondService;
    private final CryptoMarketService cryptoMarketService;
    private final StockQueryService stockQueryService;
    private final MarketFxService marketFxService;
    private final RasyonetFundService rasyonetFundService;
    private final PortfolioRedisCache portfolioRedisCache;
    private final ViopService viopService;
    private final ViopChartService viopChartService;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                PortfolioTransactionRepository portfolioTransactionRepository,
                                WatchlistItemRepository watchlistItemRepository,
                                AssetPriceQueryService assetPriceQueryService,
                                GoldMarketService goldMarketService,
                                YahooCommodityService yahooCommodityService,
                                SilverMarketService silverMarketService,
                                PreciousMetalService preciousMetalService,
                                EvdsBondService evdsBondService,
                                CryptoMarketService cryptoMarketService,
                                StockQueryService stockQueryService,
                                MarketFxService marketFxService,
                                RasyonetFundService rasyonetFundService,
                                PortfolioRedisCache portfolioRedisCache,
                                ViopService viopService,
                                ViopChartService viopChartService) {
        this.portfolioRepository              = portfolioRepository;
        this.portfolioTransactionRepository   = portfolioTransactionRepository;
        this.watchlistItemRepository          = watchlistItemRepository;
        this.assetPriceQueryService  = assetPriceQueryService;
        this.goldMarketService       = goldMarketService;
        this.yahooCommodityService   = yahooCommodityService;
        this.silverMarketService     = silverMarketService;
        this.preciousMetalService    = preciousMetalService;
        this.evdsBondService         = evdsBondService;
        this.cryptoMarketService     = cryptoMarketService;
        this.stockQueryService       = stockQueryService;
        this.marketFxService         = marketFxService;
        this.rasyonetFundService     = rasyonetFundService;
        this.portfolioRedisCache     = portfolioRedisCache;
        this.viopService             = viopService;
        this.viopChartService        = viopChartService;
    }

    // ── HOLDINGS portföy işlemleri ────────────────────────────────────────────

    @Override
    @Transactional
    public PortfolioResponse createPortfolio(String userId, CreatePortfolioRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        String name = request.getName().trim();

        if (portfolioRepository.existsByUserIdAndName(userId, name)) {
            throw new IllegalArgumentException(
                    "A portfolio with name '" + name + "' already exists for this user");
        }

        String currency = (request.getCurrency() == null || request.getCurrency().isBlank())
                ? "USD"
                : request.getCurrency().trim().toUpperCase();

        // portfolioType parse — null/blank → HOLDINGS default
        PortfolioType portfolioType = parsePortfolioType(request.getPortfolioType());

        Portfolio portfolio = new Portfolio();
        portfolio.setUserId(userId);
        portfolio.setName(name);
        portfolio.setDescription(request.getDescription());
        portfolio.setCurrency(currency);
        portfolio.setPortfolioType(portfolioType);

        portfolio = portfolioRepository.save(portfolio);
        log.debug("Created portfolio id={} type={} for userId={}", portfolio.getId(), portfolioType, userId);

        PortfolioResponse response = toPortfolioResponse(portfolio);
        portfolioRedisCache.evictList(userId);
        return response;
    }

    @Override
    @Transactional
    public PortfolioResponse addTransaction(String userId, UUID portfolioId, AddTransactionRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        // WATCHLIST portföylere transaction eklenemez
        if (portfolio.getPortfolioType() == PortfolioType.WATCHLIST) {
            throw new IllegalArgumentException(
                    "Watchlist portfolios cannot contain buy/sell transactions.");
        }

        String normalizedSymbol = normalizeSymbol(request.getAssetType(), request.getSymbol());

        if (request.getTransactionType() == TransactionType.SELL) {
            validateSellQuantity(portfolio.getTransactions(), normalizedSymbol,
                    request.getAssetType(), request.getQuantity());
        }

        BigDecimal commission = request.getCommission() != null
                ? request.getCommission()
                : BigDecimal.ZERO;

        PortfolioTransaction tx = new PortfolioTransaction();
        tx.setSymbol(normalizedSymbol);
        tx.setAssetType(request.getAssetType());
        tx.setTransactionType(request.getTransactionType());
        tx.setQuantity(request.getQuantity());
        tx.setPrice(request.getPrice());
        tx.setCommission(commission);
        tx.setTransactionDate(request.getTransactionDate());

        portfolio.addTransaction(tx);
        portfolio = portfolioRepository.save(portfolio);
        log.debug("Added transaction symbol={} to portfolioId={}", normalizedSymbol, portfolioId);

        PortfolioResponse response = toPortfolioResponse(portfolio);
        portfolioRedisCache.evictListAndDetail(userId, portfolioId);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolioById(String userId, UUID portfolioId) {
        PortfolioResponse response = portfolioRedisCache.getPortfolioDetail(userId, portfolioId)
                .orElseGet(() -> {
                    Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Portfolio not found: id=" + portfolioId + " userId=" + userId));
                    return toPortfolioResponse(portfolio);
                });
        // Redis'teki anlık snapshot fiyatları eskitir; her GET'te canlı fiyatları yeniden uygula
        // (FUTURE/VİOP gibi sonradan eklenen zenginleştirme de cache'te kalmış boş alanları giderir)
        if (response.getHoldings() != null) {
            for (PortfolioHoldingResponse h : response.getHoldings()) {
                enrichWithLivePrice(h);
            }
        }
        // Önbelleğe yazmayı zenginleştirmeden SONRA yap — aksi halde Redis'te 52w/MA olmadan snapshot kalır
        portfolioRedisCache.putPortfolioDetail(userId, portfolioId, response);
        return response;
    }

    @Override
    @Transactional
    public PortfolioResponse updatePortfolio(String userId, UUID portfolioId, UpdatePortfolioRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        String newName = request.getName().trim();
        if (!newName.equals(portfolio.getName())
                && portfolioRepository.existsByUserIdAndName(userId, newName)) {
            throw new IllegalArgumentException(
                    "A portfolio with name '" + newName + "' already exists for this user");
        }

        portfolio.setName(newName);
        portfolio.setDescription(request.getDescription());
        portfolio = portfolioRepository.save(portfolio);
        log.debug("Updated portfolio id={} for userId={}", portfolioId, userId);

        PortfolioResponse response = toPortfolioResponse(portfolio);
        portfolioRedisCache.evictListAndDetail(userId, portfolioId);
        return response;
    }

    @Override
    @Transactional
    public void deletePortfolio(String userId, UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));
        portfolioRepository.delete(portfolio);
        log.debug("Deleted portfolio id={} for userId={}", portfolioId, userId);
        portfolioRedisCache.evictListDetailAndWatchlist(userId, portfolioId);
    }

    @Override
    @Transactional
    public PortfolioResponse deleteTransaction(String userId, UUID portfolioId, UUID transactionId) {
        portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        int deleted = portfolioTransactionRepository.deleteByIdForPortfolioAndUser(
                transactionId, portfolioId, userId);
        if (deleted == 0) {
            throw new IllegalArgumentException("Transaction not found: id=" + transactionId);
        }

        log.debug("Deleted transaction id={} from portfolioId={}", transactionId, portfolioId);

        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));
        PortfolioResponse response = toPortfolioResponse(portfolio);
        portfolioRedisCache.evictListAndDetail(userId, portfolioId);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioResponse> getUserPortfolios(String userId) {
        return portfolioRedisCache.getPortfolioList(userId).orElseGet(() -> {
            List<PortfolioResponse> list = portfolioRepository.findByUserId(userId).stream()
                    .map(this::toPortfolioResponse)
                    .collect(Collectors.toList());
            portfolioRedisCache.putPortfolioList(userId, list);
            return list;
        });
    }

    // ── WATCHLIST işlemleri ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlistItems(String userId, UUID portfolioId) {
        return portfolioRedisCache.getWatchlist(userId, portfolioId).orElseGet(() -> {
            Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Portfolio not found: id=" + portfolioId + " userId=" + userId));

            requireWatchlist(portfolio);

            List<WatchlistItemResponse> list = watchlistItemRepository.findByPortfolioId(portfolioId).stream()
                    .map(this::toWatchlistItemResponse)
                    .collect(Collectors.toList());
            portfolioRedisCache.putWatchlist(userId, portfolioId, list);
            return list;
        });
    }

    @Override
    @Transactional
    public WatchlistItemResponse addWatchlistItem(String userId, UUID portfolioId,
                                                  AddWatchlistItemRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        requireWatchlist(portfolio);

        // Symbol normalize — CRYPTO lowercase, diğerleri uppercase
        String normalizedSymbol = normalizeSymbol(request.getAssetType(), request.getSymbol());

        // Duplicate kontrolü
        if (watchlistItemRepository.existsByPortfolioIdAndSymbolAndAssetType(
                portfolioId, normalizedSymbol, request.getAssetType())) {
            throw new IllegalArgumentException(
                    "Symbol '" + normalizedSymbol + "' (" + request.getAssetType()
                    + ") is already in this watchlist.");
        }

        WatchlistItem item = new WatchlistItem();
        item.setPortfolio(portfolio);
        item.setSymbol(normalizedSymbol);
        item.setAssetType(request.getAssetType());
        item.setNotes(request.getNotes());
        setWatchStartSnapshot(item);

        item = watchlistItemRepository.save(item);
        log.debug("Added watchlist item symbol={} assetType={} to portfolioId={}",
                normalizedSymbol, request.getAssetType(), portfolioId);

        WatchlistItemResponse response = toWatchlistItemResponse(item);
        portfolioRedisCache.evictListDetailAndWatchlist(userId, portfolioId);
        return response;
    }

    @Override
    @Transactional
    public void deleteWatchlistItem(String userId, UUID portfolioId, UUID itemId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        requireWatchlist(portfolio);

        watchlistItemRepository.findByIdAndPortfolioId(itemId, portfolioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Watchlist item not found: id=" + itemId + " portfolioId=" + portfolioId));

        watchlistItemRepository.deleteByIdAndPortfolioId(itemId, portfolioId);
        log.debug("Deleted watchlist item id={} from portfolioId={}", itemId, portfolioId);
        portfolioRedisCache.evictListDetailAndWatchlist(userId, portfolioId);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private PortfolioResponse toPortfolioResponse(Portfolio portfolio) {
        List<PortfolioTransactionResponse> txResponses = portfolio.getTransactions().stream()
                .sorted(java.util.Comparator
                        .comparing(PortfolioTransaction::getTransactionDate).reversed()
                        .thenComparing(PortfolioTransaction::getCreatedAt,
                                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::toTransactionResponse)
                .collect(Collectors.toList());

        List<PortfolioHoldingResponse> holdings = buildHoldings(portfolio.getTransactions());

        BigDecimal totalCost = holdings.stream()
                .map(h -> h.getTotalCost() != null ? h.getTotalCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalMarketValue = holdings.stream()
                .filter(h -> h.getMarketValue() != null)
                .map(PortfolioHoldingResponse::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalProfitLoss = holdings.stream()
                .filter(h -> h.getProfitLoss() != null)
                .map(PortfolioHoldingResponse::getProfitLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        PortfolioResponse response = new PortfolioResponse();
        response.setId(portfolio.getId());
        response.setName(portfolio.getName());
        response.setDescription(portfolio.getDescription());
        response.setCurrency(portfolio.getCurrency());
        response.setPortfolioType(portfolio.getPortfolioType());
        response.setCreatedAt(portfolio.getCreatedAt());
        response.setUpdatedAt(portfolio.getUpdatedAt());
        response.setTransactions(txResponses);
        response.setHoldings(holdings);
        response.setTotalCost(totalCost);
        response.setTotalMarketValue(totalMarketValue);
        response.setTotalProfitLoss(totalProfitLoss);

        if (portfolio.getPortfolioType() == PortfolioType.WATCHLIST) {
            response.setWatchlistItemCount(watchlistItemRepository.countByPortfolioId(portfolio.getId()));
        }

        return response;
    }

    private PortfolioTransactionResponse toTransactionResponse(PortfolioTransaction tx) {
        PortfolioTransactionResponse r = new PortfolioTransactionResponse();
        r.setId(tx.getId());
        r.setSymbol(tx.getSymbol());
        r.setAssetType(tx.getAssetType());
        r.setTransactionType(tx.getTransactionType());
        r.setQuantity(tx.getQuantity());
        r.setPrice(tx.getPrice());
        r.setCommission(tx.getCommission());
        r.setTransactionDate(tx.getTransactionDate());
        r.setCreatedAt(tx.getCreatedAt());
        return r;
    }

    private WatchlistItemResponse toWatchlistItemResponse(WatchlistItem item) {
        WatchlistItemResponse r = new WatchlistItemResponse();
        r.setId(item.getId());
        r.setSymbol(item.getSymbol());
        r.setAssetType(item.getAssetType());
        r.setNotes(item.getNotes());
        r.setAddedAt(item.getAddedAt());
        r.setStartPrice(item.getStartPrice());
        r.setStartCurrency(item.getStartCurrency());
        enrichWatchlistItemWithMarketData(r);
        return r;
    }

    private void setWatchStartSnapshot(WatchlistItem item) {
        try {
            WatchlistItemResponse tmp = new WatchlistItemResponse();
            tmp.setAssetType(item.getAssetType());
            tmp.setSymbol(item.getSymbol());
            enrichWatchlistItemWithMarketData(tmp);
            item.setStartPrice(tmp.getLastPrice());
            item.setStartCurrency(tmp.getCurrency());
        } catch (Exception ex) {
            log.debug("Failed to set start snapshot for symbol={}: {}", item.getSymbol(), ex.getMessage());
        }
    }

    private void enrichWatchlistItemWithMarketData(WatchlistItemResponse r) {
        try {
            AssetType type = r.getAssetType();
            String symbol  = r.getSymbol();
            if (type == null || symbol == null || symbol.isBlank()) return;

            switch (type) {
                case CRYPTO -> enrichCrypto(r, symbol);
                case STOCK, FUTURE -> enrichStockLike(r, symbol);
                case FUND -> enrichFund(r, symbol);
                case FX -> enrichFx(r, symbol);
                case GOLD -> enrichGold(r, symbol);
                case COMMODITY -> enrichCommodity(r, symbol);
                case BOND -> enrichBond(r, symbol);
            }
        } catch (UnsupportedOperationException ex) {
            log.debug("Watchlist live price not supported for assetType={} symbol={}",
                    r.getAssetType(), r.getSymbol());
        } catch (Exception ex) {
            log.warn("Failed to enrich watchlist item assetType={} symbol={}: {}",
                    r.getAssetType(), r.getSymbol(), ex.getMessage());
        }
    }

    private void enrichFx(WatchlistItemResponse r, String symbol) {
        String sym = symbol.toUpperCase();
        FxLatestResponse fx = marketFxService.getTcmbLatestRates(sym);
        FxRateItemDto rate = fx.getRates().stream()
                .filter(x -> sym.equalsIgnoreCase(x.getSymbol()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("FX rate not found: " + sym));

        BigDecimal buy = rate.getBuy();
        BigDecimal sell = rate.getSell();
        if (rate.getUnit() > 1) {
            BigDecimal u = BigDecimal.valueOf(rate.getUnit());
            if (buy != null) buy = buy.divide(u, 6, RoundingMode.HALF_UP);
            if (sell != null) sell = sell.divide(u, 6, RoundingMode.HALF_UP);
        }

        r.setBuy(buy);
        r.setSell(sell);
        r.setLastPrice(sell); // tabloda "son fiyat" = satış
        r.setCurrency("TRY");
        r.setAsOf(parseDateTimeLenient(fx.getAsOf()));
    }

    private void enrichCrypto(WatchlistItemResponse r, String symbol) {
        // Crypto symbol backend’de normalize: lowercase (btc)
        CryptoMarketItem item = cryptoMarketService.findBySymbol(symbol);
        r.setLastPrice(item.getCurrentPrice());
        r.setCurrency("TRY");
        r.setHigh(item.getHigh24h());
        r.setLow(item.getLow24h());
        r.setChange(item.getPriceChange24h());
        r.setChangePercent(item.getPriceChangePercentage24h());
        r.setVolume(item.getTotalVolume() != null ? item.getTotalVolume().longValue() : null);
        r.setAsOf(parseDateTimeLenient(item.getLastUpdated()));

        // Açılış kolonu için: (last - change24h) ≈ previous close (günlük açılış değil ama dolu ve tutarlı)
        if (item.getCurrentPrice() != null && item.getPriceChange24h() != null) {
            r.setOpen(item.getCurrentPrice().subtract(item.getPriceChange24h()));
        }
    }

    private void enrichStockLike(WatchlistItemResponse r, String symbol) {
        // STOCK/FUTURE: Yahoo meta → StockSummary
        StockSummary s = stockQueryService.getStockSummary(symbol.toUpperCase());
        applyStockSummary(r, s);
    }

    /**
     * Yahoo Finance ETF / hisse / VIOP: özet alanları watchlist DTO'ya aktarır.
     */
    private void applyStockSummary(WatchlistItemResponse r, StockSummary s) {
        r.setLastPrice(s.getPrice());
        r.setCurrency(s.getCurrency());
        r.setHigh(s.getDayHigh());
        r.setLow(s.getDayLow());
        r.setChange(s.getChange());
        r.setChangePercent(s.getChangePercent());
        r.setVolume(s.getVolume());
        r.setAsOf(parseDateTimeLenient(s.getAsOf()));

        if (s.getPrice() != null && s.getChange() != null) {
            r.setOpen(s.getPrice().subtract(s.getChange()));
        }
    }

    /**
     * Rasyonet card (fiyat geçmişi) öncelikli: listedeki SourceCode, yoksa TMF → TPF → TAF.
     * Card yoksa filtre listesi satırı + günlük getiri.
     */
    private void enrichFund(WatchlistItemResponse r, String symbol) {
        String code = symbol.trim().toUpperCase();

        RasyonetFundDto listed = findRasyonetFundByCode(code);
        List<String> detailSources = new ArrayList<>();
        if (listed != null && listed.getSourceCode() != null && !listed.getSourceCode().isBlank()) {
            detailSources.add(listed.getSourceCode().trim().toUpperCase());
        }
        for (String sc : List.of("TMF", "TPF", "TAF")) {
            if (!detailSources.contains(sc)) {
                detailSources.add(sc);
            }
        }

        for (String sc : detailSources) {
            RasyonetFundDetailDto d = rasyonetFundService.getFundDetailRich(code, sc);
            if (d != null && d.getPrice() != null && d.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                applyRasyonetFundDetail(r, d);
                return;
            }
        }

        if (listed != null && listed.getPrice() != null
                && listed.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            applyRasyonetFundListRow(r, listed);
            return;
        }

        throw new IllegalArgumentException("Fund price not found for code: " + code);
    }

    private RasyonetFundDto findRasyonetFundByCode(String code) {
        String u = code.toUpperCase();
        for (RasyonetFundDto f : rasyonetFundService.getAllFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) return f;
        }
        for (RasyonetFundDto f : rasyonetFundService.getAllBesFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) return f;
        }
        for (RasyonetFundDto f : rasyonetFundService.getAllOksFunds()) {
            if (f.getCode() != null && u.equalsIgnoreCase(f.getCode())) return f;
        }
        return null;
    }

    private void applyRasyonetFundListRow(WatchlistItemResponse r, RasyonetFundDto f) {
        r.setLastPrice(f.getPrice());
        r.setCurrency("TRY");
        fillFundChangeFromDailyPercent(r, f.getPrice(), f.getReturnOneDay());
        mapFundMetadataFromListDto(r, f);
        r.setAsOf(LocalDateTime.now());
    }

    private void applyRasyonetFundDetail(WatchlistItemResponse r, RasyonetFundDetailDto d) {
        BigDecimal nav = d.getPrice();
        r.setLastPrice(nav);
        String cur = d.getCurrencyCode();
        r.setCurrency(cur != null && !cur.isBlank() ? cur : "TRY");
        mapFundMetadataFromDetailDto(r, d);

        List<RasyonetFundDetailDto.PricePoint> ph = d.getPriceHistory();
        if (!applyFundOhlcFromRasyonetHistory(r, ph, nav)) {
            fillFundChangeFromDailyPercent(r, nav, d.getReturnOneDay());
        }
        if (r.getOpen() == null && r.getLastPrice() != null && r.getChange() != null) {
            r.setOpen(r.getLastPrice().subtract(r.getChange()).setScale(4, RoundingMode.HALF_UP));
        }
        r.setAsOf(LocalDateTime.now());
    }

    private void mapFundMetadataFromListDto(WatchlistItemResponse r, RasyonetFundDto f) {
        if (f == null) return;
        if (f.getName() != null && !f.getName().isBlank()) r.setFundName(f.getName());
        if (f.getFundType() != null && !f.getFundType().isBlank()) r.setFundType(f.getFundType());
        r.setFundReturnOneMonth(f.getReturnOneMonth());
        r.setFundReturnThreeMonths(f.getReturnThreeMonths());
        r.setFundReturnYtd(f.getReturnYearToDate());
        r.setFundReturnOneYear(f.getReturnOneYear());
        r.setFundRiskLevel(f.getRiskLevel());
    }

    private void mapFundMetadataFromDetailDto(WatchlistItemResponse r, RasyonetFundDetailDto d) {
        if (d == null) return;
        if (d.getName() != null && !d.getName().isBlank()) r.setFundName(d.getName());
        if (d.getFundType() != null && !d.getFundType().isBlank()) r.setFundType(d.getFundType());
        r.setFundReturnOneMonth(d.getReturnOneMonth());
        r.setFundReturnThreeMonths(d.getReturnThreeMonths());
        r.setFundReturnYtd(d.getReturnYearToDate());
        r.setFundReturnOneYear(d.getReturnOneYear());
        r.setFundRiskLevel(d.getRiskLevel());
    }

    /**
     * TEFAS’ta hisse gibi intraday OHLC yok; son birkaç günlük NAV aralığını yüksek/düşük,
     * bir önceki gün kapanışını “açılış” olarak kullanırız.
     */
    private boolean applyFundOhlcFromRasyonetHistory(WatchlistItemResponse r,
            List<RasyonetFundDetailDto.PricePoint> ph, BigDecimal nav) {
        if (ph == null || ph.isEmpty() || nav == null) {
            return false;
        }
        int n = ph.size();
        int windowStart = Math.max(0, n - 5);
        BigDecimal maxP = null;
        BigDecimal minP = null;
        for (int i = windowStart; i < n; i++) {
            BigDecimal p = ph.get(i).getPrice();
            if (p == null) continue;
            maxP = maxP == null ? p : maxP.max(p);
            minP = minP == null ? p : minP.min(p);
        }
        maxP = maxP == null ? nav : maxP.max(nav);
        minP = minP == null ? nav : minP.min(nav);
        r.setHigh(maxP.setScale(4, RoundingMode.HALF_UP));
        r.setLow(minP.setScale(4, RoundingMode.HALF_UP));

        if (n >= 2) {
            BigDecimal prevClose = ph.get(n - 2).getPrice();
            if (prevClose != null) {
                r.setOpen(prevClose.setScale(4, RoundingMode.HALF_UP));
                BigDecimal ch = nav.subtract(prevClose);
                r.setChange(ch.setScale(4, RoundingMode.HALF_UP));
                if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
                    r.setChangePercent(ch.divide(prevClose, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP));
                }
                return true;
            }
        }
        return false;
    }

    /** Rasyonet günlük getiri alanı yüzde; TL tabanlı farkı geri hesaplarız. */
    private void fillFundChangeFromDailyPercent(WatchlistItemResponse r, BigDecimal price, BigDecimal returnOneDayPct) {
        if (price == null || returnOneDayPct == null) return;
        r.setChangePercent(returnOneDayPct.setScale(2, RoundingMode.HALF_UP));
        BigDecimal denom = BigDecimal.ONE.add(
                returnOneDayPct.divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP));
        if (denom.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal impliedPrev = price.divide(denom, 8, RoundingMode.HALF_UP);
        r.setChange(price.subtract(impliedPrev).setScale(4, RoundingMode.HALF_UP));
        if (r.getLastPrice() != null && r.getChange() != null) {
            r.setOpen(r.getLastPrice().subtract(r.getChange()).setScale(4, RoundingMode.HALF_UP));
        }
    }

    private void enrichCommodity(WatchlistItemResponse r, String symbol) {
        // BIST kıymetli madenler: METAL:CATEGORY (örn: SILVER:KG_TRY)
        if (symbol.contains(":")) {
            String[] parts = symbol.split(":", 2);
            String metal   = parts[0].toUpperCase();
            String cat     = parts.length > 1 ? parts[1].toUpperCase() : "";

            if ("SILVER".equals(metal)) {
                SilverSpotResponse spot = silverMarketService.getSpotSilver();
                BigDecimal price = null;
                String currency = null;
                BigDecimal high = null;
                BigDecimal low  = null;
                Long volume     = null;
                BigDecimal open = null;
                BigDecimal change = null;
                BigDecimal changePct = null;
                LocalDateTime asOf = parseDateTimeLenient(spot.getLastUpdated());

                switch (cat) {
                    case "GRAM_TRY" -> {
                        SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "TRY");
                        LatestPrev lp = latestPrevFromSilverHistory(hist);
                        if (lp.latest != null && lp.latest.getClose() != null) {
                            price = lp.latest.getClose();
                            open  = lp.latest.getOpen();
                            high  = lp.latest.getHigh();
                            low   = lp.latest.getLow();
                            volume = lp.latest.getVolume();
                        } else {
                            price = spot.getSilverGramCloseTry();
                            high = spot.getSilverGramHighTry();
                            low  = spot.getSilverGramLowTry();
                        }
                        if (price == null) {
                            price = spot.getSilverGramTry();
                        }
                        if (lp.latest != null && lp.prev != null && lp.latest.getClose() != null
                                && lp.prev.getClose() != null) {
                            BigDecimal refPrice = price != null ? price : lp.latest.getClose();
                            change = refPrice.subtract(lp.prev.getClose());
                            if (lp.prev.getClose().compareTo(BigDecimal.ZERO) != 0) {
                                changePct = change.divide(lp.prev.getClose(), 6, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP);
                            }
                        }
                        currency = "TRY";
                    }
                    case "KG_TRY" -> {
                        // KG için spot weightedAverage kullan; open/volume için history closeTryKg üzerinden yaklaş
                        price = spot.getWeightedAverageTryKg();
                        currency = "TRY";
                        high = spot.getHighTryKg();
                        low  = spot.getLowTryKg();
                        volume = spot.getVolumeTry() != null ? spot.getVolumeTry().longValue() : null;

                        try {
                            SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "TRY");
                            LatestPrev lp = latestPrevFromSilverHistory(hist);
                            if (lp.latest != null && lp.latest.getCloseTryKg() != null) {
                                // open = önceki gün closeTryKg (sentetik)
                                open = lp.latest.getOpen() != null && lp.prev != null
                                        ? lp.prev.getCloseTryKg()
                                        : null;
                                if (lp.prev != null && lp.prev.getCloseTryKg() != null && lp.latest.getCloseTryKg() != null) {
                                    change = lp.latest.getCloseTryKg().subtract(lp.prev.getCloseTryKg());
                                    if (lp.prev.getCloseTryKg().compareTo(BigDecimal.ZERO) != 0) {
                                        changePct = change.divide(lp.prev.getCloseTryKg(), 6, RoundingMode.HALF_UP)
                                                .multiply(BigDecimal.valueOf(100))
                                                .setScale(2, RoundingMode.HALF_UP);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    case "USD_ONS" -> {
                        SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "USD");
                        LatestPrev lp = latestPrevFromSilverHistory(hist);
                        if (lp.latest != null) {
                            price = lp.latest.getClose();
                            open  = lp.latest.getOpen();
                            high  = lp.latest.getHigh();
                            low   = lp.latest.getLow();
                            volume = lp.latest.getVolume();
                            if (price != null && open != null) {
                                change = price.subtract(open);
                                if (open.compareTo(BigDecimal.ZERO) != 0) {
                                    changePct = change.divide(open, 6, RoundingMode.HALF_UP)
                                            .multiply(BigDecimal.valueOf(100))
                                            .setScale(2, RoundingMode.HALF_UP);
                                }
                            }
                        } else {
                            price = spot.getSilverUsdOns();
                        }
                        currency = "USD";
                    }
                    default -> throw new UnsupportedOperationException("Unsupported silver category: " + cat);
                }

                r.setLastPrice(price);
                r.setOpen(open);
                r.setCurrency(currency);
                r.setHigh(high);
                r.setLow(low);
                r.setChange(change);
                r.setChangePercent(changePct);
                r.setVolume(volume);
                r.setAsOf(asOf);
                return;
            }

            if ("PLATINUM".equals(metal) || "PALLADIUM".equals(metal)) {
                PreciousMetalType type = "PLATINUM".equals(metal) ? PreciousMetalType.PLATINUM : PreciousMetalType.PALLADIUM;
                PreciousMetalSpotResponse spot = preciousMetalService.getSpot(type);
                String histCurrency = switch (cat) {
                    case "USD_ONS" -> "USD";
                    case "EUR_ONS" -> "EUR";
                    default -> "TRY";
                };
                PreciousMetalHistoryResponse hist = preciousMetalService.getHistory(type, "1W", histCurrency);

                BigDecimal price;
                String currency;
                switch (cat) {
                    case "GRAM_TRY" -> { price = spot.getTryGram(); currency = "TRY"; }
                    case "KG_TRY"   -> { price = spot.getTryKg();   currency = "TRY"; }
                    case "USD_ONS"  -> { price = spot.getUsdOns();  currency = "USD"; }
                    case "EUR_ONS"  -> { price = spot.getEurOns();  currency = "EUR"; }
                    default -> throw new UnsupportedOperationException("Unsupported precious category: " + cat);
                }

                r.setLastPrice(price);
                r.setCurrency(currency);

                // BIST günlük seride mum/HLC yok; son ~5 günün referans fiyat aralığı → yüksek/düşük
                applyPreciousHighLowFromHistoryWindow(r, hist, cat, price);

                // “Açılış” için: bir önceki gün value (history) → open
                // change/change% hesapla (hacim BIST metal günlük kaynakta yok — gümüşten farklı)
                try {
                    LatestPrevPrecious pp = latestPrevFromPreciousHistory(hist);
                    if (pp.latest != null && pp.prev != null) {
                        BigDecimal last = pickPreciousValue(pp.latest, cat);
                        BigDecimal prev = pickPreciousValue(pp.prev, cat);
                        if (last != null && prev != null) {
                            r.setOpen(prev);
                            BigDecimal ch = last.subtract(prev);
                            r.setChange(ch);
                            if (prev.compareTo(BigDecimal.ZERO) != 0) {
                                r.setChangePercent(ch.divide(prev, 6, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP));
                            }
                        }
                    }
                } catch (Exception ignored) {}

                r.setAsOf(parseDateTimeLenient(spot.getLastUpdated()));
                return;
            }

            throw new UnsupportedOperationException("Unsupported precious metal symbol: " + symbol);
        }

        // Yahoo Finance emtialar
        CommoditySpotDto spot = yahooCommodityService.getSpot(symbol);
        r.setLastPrice(spot.getDisplayPrice());
        r.setCurrency(spot.getDisplayCurrency());
        r.setOpen(spot.getPreviousClose());
        r.setHigh(spot.getDayHigh());
        r.setLow(spot.getDayLow());
        r.setChange(spot.getChange());
        r.setChangePercent(spot.getChangePercent());
        r.setVolume(spot.getVolume());
        r.setAsOf(parseDateTimeLenient(spot.getLastUpdated()));
    }

    private void enrichGold(WatchlistItemResponse r, String symbol) {
        GoldSpotResponse spot = goldMarketService.getSpotGold();
        LocalDateTime asOf = parseDateTimeLenient(spot.getLastUpdated());
        if (asOf == null) asOf = parseDateTimeLenient(spot.getUpdatedAt());
        r.setAsOf(asOf);

        String upper = symbol.toUpperCase();
        switch (upper) {
            case "GOLD" -> {
                BigDecimal onsTry = spot.getOnsTry();
                if (onsTry == null && spot.getOnsUsd() != null && spot.getUsdTry() != null) {
                    onsTry = spot.getOnsUsd().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP);
                }
                r.setLastPrice(onsTry);
                r.setCurrency("TRY");
                if (spot.getOnsHigh() != null && spot.getUsdTry() != null) {
                    r.setHigh(spot.getOnsHigh().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP));
                } else {
                    r.setHigh(spot.getOnsHigh());
                }
                if (spot.getOnsLow() != null && spot.getUsdTry() != null) {
                    r.setLow(spot.getOnsLow().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP));
                } else {
                    r.setLow(spot.getOnsLow());
                }
                if (spot.getOnsChange() != null && spot.getUsdTry() != null) {
                    r.setChange(spot.getOnsChange().multiply(spot.getUsdTry()).setScale(2, RoundingMode.HALF_UP));
                } else {
                    r.setChange(spot.getOnsChange());
                }
                r.setChangePercent(spot.getOnsChangePercent());
            }
            case "GRAM" -> {
                r.setLastPrice(spot.getGramGoldTry());
                r.setCurrency("TRY");
                r.setHigh(spot.getGramHighTry());
                r.setLow(spot.getGramLowTry());
            }
            case "CEYREK" -> { r.setLastPrice(spot.getQuarterGoldTry()); r.setCurrency("TRY"); }
            case "YARIM"  -> { r.setLastPrice(spot.getHalfGoldTry());    r.setCurrency("TRY"); }
            case "TAM", "ZIYNET" -> { r.setLastPrice(spot.getZiynetGoldTry());  r.setCurrency("TRY"); }
            case "CUMHUR", "ATA" -> { r.setLastPrice(spot.getRepublicGoldTry()); r.setCurrency("TRY"); }
            case "14AYAR", "AYAR14" -> {
                r.setLastPrice(spot.getFourteenKBraceletTry() != null ? spot.getFourteenKBraceletTry() : spot.getAyar14Tl());
                r.setCurrency("TRY");
            }
            case "22AYAR", "AYAR22" -> {
                r.setLastPrice(spot.getTwentyTwoKBraceletTry() != null ? spot.getTwentyTwoKBraceletTry() : spot.getAyar22Tl());
                r.setCurrency("TRY");
            }
            default -> throw new UnsupportedOperationException("Unsupported gold symbol: " + symbol);
        }

        // “Açılış” ve (varsa) hacim için history’den son nokta
        try {
            if ("GOLD".equals(upper)) {
                GoldHistoryResponse hist = goldMarketService.getGoldHistory("1W", "USD");
                GoldHistoryPoint lp = latestFromGoldHistory(hist);
                if (lp != null) {
                    r.setOpen(lp.getOpen());
                    r.setHigh(lp.getHigh() != null ? lp.getHigh() : r.getHigh());
                    r.setLow(lp.getLow() != null ? lp.getLow() : r.getLow());
                    r.setVolume(lp.getVolume());
                }
            } else {
                GoldHistoryResponse hist = goldMarketService.getGoldHistory("1W", "TRY");
                GoldHistoryPoint lp = latestFromGoldHistory(hist);
                if (lp != null) {
                    // Gram’da open = önceki gün close (GoldHistoryPoint.open zaten sentetik)
                    if ("GRAM".equals(upper)) {
                        r.setOpen(lp.getOpen());
                        r.setVolume(lp.getVolume());
                        // Gram satırında lastPrice teorik gram; close daha “gerçek”
                        if (lp.getClose() != null) r.setLastPrice(lp.getClose());
                        r.setHigh(lp.getHigh());
                        r.setLow(lp.getLow());
                        if (lp.getClose() != null && lp.getOpen() != null) {
                            BigDecimal ch = lp.getClose().subtract(lp.getOpen());
                            r.setChange(ch);
                            if (lp.getOpen().compareTo(BigDecimal.ZERO) != 0) {
                                r.setChangePercent(ch.divide(lp.getOpen(), 6, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP));
                            }
                        }
                    } else {
                        // Çeyrek/yarım/tam vb.: günlük fark BIST gram OHLC ile aynı yüzde hareket varsayımıyla ölçeklenir
                        BigDecimal gramRef = spot.getOfficialPureGoldGramTry() != null
                                ? spot.getOfficialPureGoldGramTry()
                                : spot.getGramGoldTry();
                        if (gramRef != null && gramRef.compareTo(BigDecimal.ZERO) != 0
                                && lp.getClose() != null && lp.getOpen() != null
                                && r.getLastPrice() != null) {
                            BigDecimal gramCh = lp.getClose().subtract(lp.getOpen());
                            BigDecimal ratio = r.getLastPrice().divide(gramRef, 8, RoundingMode.HALF_UP);
                            BigDecimal coinChange = gramCh.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
                            r.setChange(coinChange);
                            if (lp.getOpen().compareTo(BigDecimal.ZERO) != 0) {
                                BigDecimal gramPct = gramCh.divide(lp.getOpen(), 8, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP);
                                r.setChangePercent(gramPct);
                            }
                            r.setOpen(r.getLastPrice().subtract(coinChange));
                            if (lp.getHigh() != null) {
                                r.setHigh(lp.getHigh().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
                            }
                            if (lp.getLow() != null) {
                                r.setLow(lp.getLow().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
                            }
                            r.setVolume(lp.getVolume());
                        } else {
                            r.setVolume(lp.getVolume());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void enrichBond(WatchlistItemResponse r, String instrumentCode) {
        EvdsBondInstrument bond = evdsBondService.getEvdsBondDetail(instrumentCode);
        r.setLastPrice(bond.getIndicatorValue());
        r.setChange(bond.getDailyChange());
        r.setChangePercent(bond.getDailyChangePercent());
        r.setCurrency("TRY");
        r.setRemainingDays(bond.getRemainingDays());
        r.setCouponRate(bond.getCouponRate());

        LocalDate lu = bond.getLastUpdated();
        r.setAsOf(lu != null ? lu.atStartOfDay() : LocalDateTime.now());
    }

    /**
     * BOND: TCMB EVDS gösterge değeri (tahvil detay sayfası ile aynı kaynak).
     * Ortalama alış / toplam maliyet işlemlerden gelir; güncel fiyat burada set edilir.
     */
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

        // ~1 yıl EVDS gösterge kapanışları → 52 hafta bandı + MA20/MA50 (trend; Bloomberg işlem hacmi yok)
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
                    holding.setMa20(computeMA(closes, 20));
                    holding.setMa50(computeMA(closes, 50));
                }
            }
        } catch (Exception e) {
            log.debug("Bond history / MA unavailable for {}: {}", code, e.getMessage());
        }
    }

    private LocalDateTime parseDateTimeLenient(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value); } catch (Exception ignored) {}
        try { return OffsetDateTime.parse(value).toLocalDateTime(); } catch (Exception ignored) {}
        try { return LocalDate.parse(value).atStartOfDay(); } catch (Exception ignored) {}
        try { return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay(); } catch (Exception ignored) {}
        try {
            // "2026-05-10 12:34"
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (Exception ignored) {}
        return null;
    }

    private static class LatestPrev {
        final SilverHistoryPoint latest;
        final SilverHistoryPoint prev;
        LatestPrev(SilverHistoryPoint latest, SilverHistoryPoint prev) {
            this.latest = latest;
            this.prev = prev;
        }
    }

    private LatestPrev latestPrevFromSilverHistory(SilverHistoryResponse resp) {
        if (resp == null || resp.getPoints() == null || resp.getPoints().isEmpty()) {
            return new LatestPrev(null, null);
        }
        List<SilverHistoryPoint> pts = resp.getPoints();
        SilverHistoryPoint latest = pts.get(pts.size() - 1);
        SilverHistoryPoint prev = pts.size() >= 2 ? pts.get(pts.size() - 2) : null;
        return new LatestPrev(latest, prev);
    }

    private static class LatestPrevPrecious {
        final PreciousMetalHistoryPoint latest;
        final PreciousMetalHistoryPoint prev;
        LatestPrevPrecious(PreciousMetalHistoryPoint latest, PreciousMetalHistoryPoint prev) {
            this.latest = latest;
            this.prev = prev;
        }
    }

    private LatestPrevPrecious latestPrevFromPreciousHistory(PreciousMetalHistoryResponse resp) {
        if (resp == null || resp.getPoints() == null || resp.getPoints().isEmpty()) {
            return new LatestPrevPrecious(null, null);
        }
        List<PreciousMetalHistoryPoint> pts = resp.getPoints();
        PreciousMetalHistoryPoint latest = pts.get(pts.size() - 1);
        PreciousMetalHistoryPoint prev = pts.size() >= 2 ? pts.get(pts.size() - 2) : null;
        return new LatestPrevPrecious(latest, prev);
    }

    private BigDecimal pickPreciousValue(PreciousMetalHistoryPoint p, String cat) {
        if (p == null) return null;
        return switch (cat) {
            case "GRAM_TRY" -> p.getTryGram();
            case "KG_TRY"   -> p.getTryKg();
            case "USD_ONS"  -> p.getUsdOns();
            case "EUR_ONS"  -> p.getEurOns();
            default -> p.getValue();
        };
    }

    /**
     * Platin/Paladyum BIST günlük kaynağında tek referans fiyat var (OHLC/hacim yok).
     * Son işlem gününe yakın birkaç güne yayılarak min/max alınır; güncel spot aralığa dahil edilir.
     */
    private void applyPreciousHighLowFromHistoryWindow(WatchlistItemResponse r,
            PreciousMetalHistoryResponse hist, String cat, BigDecimal spotPrice) {
        if (hist == null || hist.getPoints() == null || hist.getPoints().isEmpty()) {
            return;
        }
        List<PreciousMetalHistoryPoint> pts = hist.getPoints();
        int n = pts.size();
        int windowStart = Math.max(0, n - 5);
        BigDecimal maxV = null;
        BigDecimal minV = null;
        for (int i = windowStart; i < n; i++) {
            BigDecimal v = pickPreciousValue(pts.get(i), cat);
            if (v == null) continue;
            maxV = maxV == null ? v : maxV.max(v);
            minV = minV == null ? v : minV.min(v);
        }
        if (spotPrice != null) {
            maxV = maxV == null ? spotPrice : maxV.max(spotPrice);
            minV = minV == null ? spotPrice : minV.min(spotPrice);
        }
        if (maxV != null) {
            r.setHigh(maxV.setScale(4, RoundingMode.HALF_UP));
        }
        if (minV != null) {
            r.setLow(minV.setScale(4, RoundingMode.HALF_UP));
        }
    }

    private GoldHistoryPoint latestFromGoldHistory(GoldHistoryResponse resp) {
        if (resp == null || resp.getPoints() == null || resp.getPoints().isEmpty()) return null;
        List<GoldHistoryPoint> pts = resp.getPoints();
        return pts.get(pts.size() - 1);
    }

    /**
     * Verilen kapanış fiyatı listesinin son {@code period} elemanının basit hareketli ortalamasını hesaplar.
     * Liste {@code period}'dan kısa ise {@code null} döner.
     */
    private BigDecimal computeMA(List<BigDecimal> closes, int period) {
        if (closes == null || closes.size() < period) return null;
        List<BigDecimal> window = closes.subList(closes.size() - period, closes.size());
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal c : window) {
            if (c != null) { sum = sum.add(c); count++; }
        }
        if (count == 0) return null;
        return sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    // ── Holding aggregation ───────────────────────────────────────────────────

    private List<PortfolioHoldingResponse> buildHoldings(List<PortfolioTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<PortfolioTransaction>> byKey = new LinkedHashMap<>();
        for (PortfolioTransaction tx : transactions) {
            String keyPart = groupingKeyForTransaction(tx);
            String key = keyPart + "::" + tx.getAssetType().name();
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
        }

        List<PortfolioHoldingResponse> result = new ArrayList<>();

        for (Map.Entry<String, List<PortfolioTransaction>> e : byKey.entrySet()) {
            List<PortfolioTransaction> txs = new ArrayList<>(e.getValue());
            txs.sort(Comparator
                    .comparing(PortfolioTransaction::getTransactionDate)
                    .thenComparing(PortfolioTransaction::getId));

            PortfolioTransaction sample = txs.get(0);
            AssetType assetType = sample.getAssetType();
            String accSymbol = assetType == AssetType.FUTURE
                    ? canonicalFutureDisplaySymbol(sample.getSymbol())
                    : sample.getSymbol();
            HoldingAccumulator acc = new HoldingAccumulator(accSymbol, assetType);
            for (PortfolioTransaction tx : txs) {
                acc.apply(tx);
            }

            if (acc.openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            int moneyScale = assetType == AssetType.FUND ? FUND_MONEY_SCALE : MONEY_SCALE;
            int realizedPctScale = assetType == AssetType.FUND ? 8 : 2;

            PortfolioHoldingResponse holding = new PortfolioHoldingResponse();
            holding.setSymbol(acc.symbol);
            holding.setAssetType(acc.assetType);
            holding.setTotalQuantity(acc.openQuantity.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
            holding.setAverageCost(acc.averageOpenCost().setScale(moneyScale, RoundingMode.HALF_UP));
            holding.setTotalCost(acc.openCostBasis.setScale(moneyScale, RoundingMode.HALF_UP));
            holding.setFirstBuyDate(acc.firstBuyDate);
            holding.setLastTransactionDate(txs.get(txs.size() - 1).getTransactionDate());

            if (acc.anySell) {
                holding.setRealizedGainLoss(acc.realizedGainLossSum.setScale(moneyScale, RoundingMode.HALF_UP));
                if (acc.totalSoldCostBasis.compareTo(BigDecimal.ZERO) > 0) {
                    holding.setRealizedGainLossPercent(
                            acc.realizedGainLossSum
                                    .divide(acc.totalSoldCostBasis, 10, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(realizedPctScale, RoundingMode.HALF_UP));
                }
            }

            enrichWithLivePrice(holding);
            result.add(holding);
        }

        return result;
    }

    private static String groupingKeyForTransaction(PortfolioTransaction tx) {
        if (tx.getAssetType() == AssetType.FUTURE) {
            return ViopService.portfolioFutureGroupKey(tx.getSymbol());
        }
        return tx.getSymbol() != null ? tx.getSymbol() : "";
    }

    private static String canonicalFutureDisplaySymbol(String raw) {
        String n = ViopService.normalizeStoredFutureSymbol(raw);
        return n != null ? n : (raw != null ? raw : "");
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

    private void enrichWithLivePrice(PortfolioHoldingResponse holding) {
        try {
            AssetType type = holding.getAssetType();
            if (type == AssetType.STOCK) {
                enrichStockOrFutureHolding(holding);
            } else if (type == AssetType.FUTURE) {
                enrichFutureHolding(holding);
            } else if (type == AssetType.CRYPTO) {
                enrichCryptoHolding(holding);
            } else if (type == AssetType.GOLD) {
                enrichGoldHolding(holding);
            } else if (type == AssetType.COMMODITY) {
                enrichCommodityHolding(holding);
            } else if (type == AssetType.FUND) {
                enrichFundHolding(holding);
            } else if (type == AssetType.BOND) {
                enrichBondHolding(holding);
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
        }
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
        holding.setAsOf(parseDateTimeLenient(summary.getAsOf()));
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
            holding.setMa20(computeMA(closes, 20));
            holding.setMa50(computeMA(closes, 50));
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

        final ViopContractDetailDto d;
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
        LocalDateTime asOf = parseDateTimeLenient(d.getTime());
        holding.setAsOf(asOf != null ? asOf : LocalDateTime.now());

        holding.setChangePercent(d.getChangePercent());
        holding.setDayHigh(d.getHigh());
        holding.setDayLow(d.getLow());

        BigDecimal prevSet = d.getPrevSettlementPrice();
        if (prevSet != null) {
            holding.setChange(current.subtract(prevSet).multiply(multiplier)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }

        if (d.getOpenPositionCount() != null) {
            holding.setVolume(d.getOpenPositionCount());
        }

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
        List<ViopChartPointDto> pts = null;
        try {
            for (ViopChartPeriod p : List.of(
                    ViopChartPeriod.ONE_YEAR,
                    ViopChartPeriod.SIX_MONTHS,
                    ViopChartPeriod.THREE_MONTHS,
                    ViopChartPeriod.ONE_MONTH)) {
                List<ViopChartPointDto> chunk = viopChartService.getChart(trimmed, p);
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
        List<ViopChartPointDto> sorted = new ArrayList<>(pts);
        sorted.sort(Comparator.comparing(ViopChartPointDto::getTimestamp, Comparator.nullsLast(Long::compareTo)));

        List<BigDecimal> allVals = new ArrayList<>();
        TreeMap<LocalDate, BigDecimal> dailyLast = new TreeMap<>();
        for (ViopChartPointDto p : sorted) {
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
        BigDecimal ma20 = computeMA(dailyCloses, 20);
        BigDecimal ma50 = computeMA(dailyCloses, 50);
        if (ma20 != null) {
            holding.setMa20(ma20);
        }
        if (ma50 != null) {
            holding.setMa50(ma50);
        }
    }

    private static LocalDate chartPointToLocalDate(ViopChartPointDto p) {
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
     * CRYPTO: CryptoMarketItem üzerinden zenginleştirilmiş holding verisi.
     * 24h yüksek/düşük, hacim, market cap ve fiyat değişimi doldurulur.
     */
    private void enrichCryptoHolding(PortfolioHoldingResponse holding) {
        CryptoMarketItem item = cryptoMarketService.findBySymbol(holding.getSymbol());

        BigDecimal price = item.getCurrentPrice();
        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setAsOf(parseDateTimeLenient(item.getLastUpdated()));
        holding.setName(item.getName());
        holding.setChange(item.getPriceChange24h());
        holding.setChangePercent(item.getPriceChangePercentage24h());
        holding.setPriceChangePercentage7d(item.getPriceChangePercentage7d());
        holding.setVolume(item.getTotalVolume() != null ? item.getTotalVolume().longValue() : null);
        holding.setDayHigh(item.getHigh24h());
        holding.setDayLow(item.getLow24h());
        holding.setMarketCap(item.getMarketCap());
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
        FxLatestResponse latest = marketFxService.getTcmbLatestRates(symbol);
        FxRateItemDto rate = latest.getRates().stream()
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
        holding.setAsOf(parseDateTimeLenient(latest.getAsOf()));
        holding.setName(symbol + "/TRY");

        // 2) Tarihsel veriden günlük değişim, 52w aralığı, dönemsel getiriler, MA
        try {
            FxHistoryResponse hist = marketFxService.getFxHistory(symbol, "1Y");
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
                holding.setMa20(computeMA(closes, 20));
                holding.setMa50(computeMA(closes, 50));
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
        BigDecimal ma20 = computeMA(closes, 20);
        BigDecimal ma50 = computeMA(closes, 50);
        if (ma20 != null) {
            holding.setMa20(ma20);
        }
        if (ma50 != null) {
            holding.setMa50(ma50);
        }
    }

    private BigDecimal fetchUsdTryRate() {
        try {
            FxLatestResponse latest = marketFxService.getTcmbLatestRates("USD");
            if (latest == null || latest.getRates() == null) {
                return null;
            }
            return latest.getRates().stream()
                    .filter(r -> "USD".equalsIgnoreCase(r.getSymbol()))
                    .map(FxRateItemDto::getSell)
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
        LocalDateTime asOf = parseDateTimeLenient(spot.getLastUpdated());
        if (asOf == null) asOf = parseDateTimeLenient(spot.getUpdatedAt());

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
        holding.setAsOf(parseDateTimeLenient(spot.getLastUpdated()));
        String displayName = spot.getDisplayNameTr() != null ? spot.getDisplayNameTr() : spot.getDisplayNameEn();
        if (displayName != null) holding.setName(displayName);
        holding.setChange(spot.getChange());
        holding.setChangePercent(spot.getChangePercent());
        holding.setVolume(spot.getVolume());
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
                LatestPrev lp = latestPrevFromSilverHistory(hist);
                if (lp.latest != null && lp.latest.getClose() != null) {
                    price = lp.latest.getClose();
                    high  = lp.latest.getHigh();
                    low   = lp.latest.getLow();
                } else {
                    price = spot.getSilverGramCloseTry();
                    high  = spot.getSilverGramHighTry();
                    low   = spot.getSilverGramLowTry();
                }
                if (price == null) {
                    price = spot.getSilverGramTry();
                }
                if (lp.latest != null && lp.prev != null && lp.latest.getClose() != null
                        && lp.prev.getClose() != null && lp.prev.getClose().compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal refPrice = price != null ? price : lp.latest.getClose();
                    change = refPrice.subtract(lp.prev.getClose());
                    changePercent = change.divide(lp.prev.getClose(), 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            case "KG_TRY" -> {
                price = spot.getWeightedAverageTryKg();
                high  = spot.getHighTryKg();
                low   = spot.getLowTryKg();
            }
            case "USD_ONS" -> {
                SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "USD");
                LatestPrev lp = latestPrevFromSilverHistory(hist);
                if (lp.latest != null) {
                    price = lp.latest.getClose();
                    high  = lp.latest.getHigh();
                    low   = lp.latest.getLow();
                    if (price != null && lp.prev != null && lp.prev.getClose() != null
                            && lp.prev.getClose().compareTo(BigDecimal.ZERO) != 0) {
                        change = price.subtract(lp.prev.getClose());
                        changePercent = change.divide(lp.prev.getClose(), 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    price    = spot.getSilverUsdOns();
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
        holding.setAsOf(parseDateTimeLenient(spot.getLastUpdated()));
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

        // Volume — BIST silver işlem hacmi
        if (spot.getQuantityKg() != null) {
            holding.setVolume(spot.getQuantityKg().longValue());
        } else if (spot.getVolumeTry() != null && price != null && price.compareTo(BigDecimal.ZERO) != 0) {
            holding.setVolume(spot.getVolumeTry().divide(price, 0, RoundingMode.HALF_UP).longValue());
        }
    }

    /**
     * FUND: Rasyonet kart / liste — NAV, getiriler, günlük değişim alanları, ~1 yıl NAV geçmişi (52w / MA).
     */
    private void enrichFundHolding(PortfolioHoldingResponse holding) {
        String code = holding.getSymbol().trim().toUpperCase();

        RasyonetFundDto listed = findRasyonetFundByCode(code);
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
        holding.setMa20(computeMA(forMa, 20));
        holding.setMa50(computeMA(forMa, 50));
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

    // ── SELL validation ───────────────────────────────────────────────────────

    private void validateSellQuantity(List<PortfolioTransaction> existing,
                                      String symbol, AssetType assetType,
                                      BigDecimal sellQty) {
        BigDecimal currentQty = existing.stream()
                .filter(tx -> transactionSymbolMatches(assetType, symbol, tx.getSymbol(), tx.getAssetType()))
                .reduce(BigDecimal.ZERO, (acc, tx) -> {
                    if (tx.getTransactionType() == TransactionType.BUY) {
                        return acc.add(tx.getQuantity());
                    } else {
                        return acc.subtract(tx.getQuantity());
                    }
                }, BigDecimal::add);

        if (sellQty.compareTo(currentQty) > 0) {
            throw new IllegalArgumentException(
                    "Insufficient quantity for SELL: available=" + currentQty
                    + " requested=" + sellQty
                    + " symbol=" + symbol);
        }
    }

    private static boolean transactionSymbolMatches(AssetType requestAssetType,
                                                    String normalizedRequestSymbol,
                                                    String txSymbol,
                                                    AssetType txAssetType) {
        if (requestAssetType != txAssetType) {
            return false;
        }
        if (requestAssetType == AssetType.FUTURE) {
            return ViopService.portfolioFutureGroupKey(normalizedRequestSymbol)
                    .equals(ViopService.portfolioFutureGroupKey(txSymbol));
        }
        return normalizedRequestSymbol.equals(txSymbol);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * portfolioType string'ini parse eder.
     * null / blank → HOLDINGS default.
     * Geçersiz değer → anlamlı exception.
     */
    private PortfolioType parsePortfolioType(String raw) {
        if (raw == null || raw.isBlank()) {
            return PortfolioType.HOLDINGS;
        }
        try {
            return PortfolioType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid portfolioType: '" + raw + "'. Allowed values: HOLDINGS, WATCHLIST");
        }
    }

    /**
     * Portfolio'nun WATCHLIST tipinde olduğunu doğrular.
     * Değilse anlamlı exception fırlatır.
     */
    private void requireWatchlist(Portfolio portfolio) {
        if (portfolio.getPortfolioType() != PortfolioType.WATCHLIST) {
            throw new IllegalArgumentException(
                    "Only watchlist portfolios can have watchlist items. "
                    + "Portfolio '" + portfolio.getName() + "' is of type "
                    + portfolio.getPortfolioType() + ".");
        }
    }

    /**
     * STOCK → uppercase (örn: "thyao.is" → "THYAO.IS")
     * CRYPTO → lowercase (örn: "BTC" → "btc") — CoinGecko symbol formatıyla uyumlu
     * FUTURE (VİOP) → Akbank listesi ile uyum için trim/spacing/nokta + {@link Locale#ROOT} uppercase
     * Diğerleri → uppercase ({@link String#toUpperCase()} — JVM default locale)
     */
    private String normalizeSymbol(AssetType assetType, String symbol) {
        if (symbol == null) return null;
        if (assetType == AssetType.CRYPTO) {
            return symbol.trim().toLowerCase();
        }
        if (assetType == AssetType.FUTURE) {
            String n = ViopService.normalizeStoredFutureSymbol(symbol);
            return n != null ? n : symbol.trim().toUpperCase(Locale.ROOT);
        }
        return symbol.trim().toUpperCase();
    }

    // ── Inner accumulator ─────────────────────────────────────────────────────

    private static class HoldingAccumulator {

        final String symbol;
        final AssetType assetType;

        /** Remaining position quantity (open lots). */
        BigDecimal openQuantity = BigDecimal.ZERO;
        /** Remaining cost basis for open position (weighted average pool). */
        BigDecimal openCostBasis = BigDecimal.ZERO;

        BigDecimal realizedGainLossSum = BigDecimal.ZERO;
        BigDecimal totalSoldCostBasis = BigDecimal.ZERO;
        boolean anySell = false;

        /** Earliest BUY transactionDate for this symbol + assetType. */
        LocalDateTime firstBuyDate = null;

        HoldingAccumulator(String symbol, AssetType assetType) {
            this.symbol    = symbol;
            this.assetType = assetType;
        }

        void apply(PortfolioTransaction tx) {
            BigDecimal qty        = tx.getQuantity();
            BigDecimal price      = tx.getPrice();
            BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;
            LocalDateTime txDate  = tx.getTransactionDate();

            if (tx.getTransactionType() == TransactionType.BUY) {
                BigDecimal cost = qty.multiply(price).add(commission);
                openCostBasis = openCostBasis.add(cost);
                openQuantity  = openQuantity.add(qty);

                if (txDate != null && (firstBuyDate == null || txDate.isBefore(firstBuyDate))) {
                    firstBuyDate = txDate;
                }
            } else {
                // SELL — realized P/L vs average cost basis before this sell
                if (openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException(
                            "SELL with non-positive open quantity for " + symbol + " " + assetType);
                }

                BigDecimal soldCostBasis;
                if (qty.compareTo(openQuantity) >= 0) {
                    soldCostBasis = openCostBasis;
                } else {
                    BigDecimal unitCost = openCostBasis.divide(openQuantity, 12, RoundingMode.HALF_UP);
                    soldCostBasis = qty.multiply(unitCost);
                }

                BigDecimal proceeds = qty.multiply(price).subtract(commission);
                BigDecimal pnlSlice = proceeds.subtract(soldCostBasis);

                realizedGainLossSum = realizedGainLossSum.add(pnlSlice);
                totalSoldCostBasis    = totalSoldCostBasis.add(soldCostBasis);
                anySell = true;

                openCostBasis = openCostBasis.subtract(soldCostBasis);
                openQuantity  = openQuantity.subtract(qty);
            }
        }

        BigDecimal averageOpenCost() {
            if (openQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return openCostBasis.divide(openQuantity, 8, RoundingMode.HALF_UP);
        }
    }
}
