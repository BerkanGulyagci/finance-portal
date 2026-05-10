package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
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
import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import com.finance.portal.market.crypto.application.CryptoMarketItem;
import com.finance.portal.market.crypto.application.CryptoMarketService;
import com.finance.portal.market.application.service.MarketFxService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PortfolioServiceImpl implements PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioServiceImpl.class);
    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 4;

    private final PortfolioRepository portfolioRepository;
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

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
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
                                PortfolioRedisCache portfolioRedisCache) {
        this.portfolioRepository     = portfolioRepository;
        this.watchlistItemRepository = watchlistItemRepository;
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
        return portfolioRedisCache.getPortfolioDetail(userId, portfolioId).orElseGet(() -> {
            Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Portfolio not found: id=" + portfolioId + " userId=" + userId));
            PortfolioResponse response = toPortfolioResponse(portfolio);
            portfolioRedisCache.putPortfolioDetail(userId, portfolioId, response);
            return response;
        });
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
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        PortfolioTransaction tx = portfolio.getTransactions().stream()
                .filter(t -> transactionId.equals(t.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found: id=" + transactionId));

        portfolio.removeTransaction(tx);
        portfolio = portfolioRepository.save(portfolio);
        log.debug("Deleted transaction id={} from portfolioId={}", transactionId, portfolioId);
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
                        // Spot gram = weightedAverage; tablo için close/open/high/low/volume daha anlamlı → history’den çek
                        SilverHistoryResponse hist = silverMarketService.getSilverHistory("1W", "TRY");
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
                            price = spot.getSilverGramTry();
                            high = spot.getSilverGramHighTry();
                            low  = spot.getSilverGramLowTry();
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
                r.setLastPrice(spot.getOnsUsd());
                r.setCurrency("USD");
                r.setHigh(spot.getOnsHigh());
                r.setLow(spot.getOnsLow());
                r.setChange(spot.getOnsChange());
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
            case "TAM"    -> { r.setLastPrice(spot.getZiynetGoldTry());  r.setCurrency("TRY"); }
            case "CUMHUR", "ATA" -> { r.setLastPrice(spot.getRepublicGoldTry()); r.setCurrency("TRY"); }
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

    // ── Holding aggregation ───────────────────────────────────────────────────

    private List<PortfolioHoldingResponse> buildHoldings(List<PortfolioTransaction> transactions) {
        Map<String, HoldingAccumulator> accMap = new LinkedHashMap<>();

        for (PortfolioTransaction tx : transactions) {
            String key = tx.getSymbol() + "::" + tx.getAssetType().name();
            accMap.computeIfAbsent(key, k -> new HoldingAccumulator(tx.getSymbol(), tx.getAssetType()));
            accMap.get(key).apply(tx);
        }

        List<PortfolioHoldingResponse> result = new ArrayList<>();

        for (HoldingAccumulator acc : accMap.values()) {
            if (acc.totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            PortfolioHoldingResponse holding = new PortfolioHoldingResponse();
            holding.setSymbol(acc.symbol);
            holding.setAssetType(acc.assetType);
            holding.setTotalQuantity(acc.totalQuantity.setScale(PRICE_SCALE, RoundingMode.HALF_UP));
            holding.setAverageCost(acc.averageCost().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            holding.setTotalCost(acc.averageCost()
                    .multiply(acc.totalQuantity)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP));

            enrichWithLivePrice(holding);
            result.add(holding);
        }

        return result;
    }

    private void enrichWithLivePrice(PortfolioHoldingResponse holding) {
        try {
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

        } catch (UnsupportedOperationException ex) {
            log.debug("Live price not supported for assetType={} symbol={}",
                    holding.getAssetType(), holding.getSymbol());
        } catch (Exception ex) {
            log.warn("Failed to fetch live price for assetType={} symbol={}: {}",
                    holding.getAssetType(), holding.getSymbol(), ex.getMessage());
        }
    }

    // ── SELL validation ───────────────────────────────────────────────────────

    private void validateSellQuantity(List<PortfolioTransaction> existing,
                                      String symbol, AssetType assetType,
                                      BigDecimal sellQty) {
        BigDecimal currentQty = existing.stream()
                .filter(tx -> symbol.equals(tx.getSymbol()) && assetType == tx.getAssetType())
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
     * Diğerleri → uppercase
     */
    private String normalizeSymbol(AssetType assetType, String symbol) {
        if (symbol == null) return null;
        if (assetType == AssetType.CRYPTO) {
            return symbol.trim().toLowerCase();
        }
        return symbol.trim().toUpperCase();
    }

    // ── Inner accumulator ─────────────────────────────────────────────────────

    private static class HoldingAccumulator {

        final String symbol;
        final AssetType assetType;

        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalBuyCost  = BigDecimal.ZERO;
        BigDecimal totalBuyQty   = BigDecimal.ZERO;

        HoldingAccumulator(String symbol, AssetType assetType) {
            this.symbol    = symbol;
            this.assetType = assetType;
        }

        void apply(PortfolioTransaction tx) {
            BigDecimal qty        = tx.getQuantity();
            BigDecimal price      = tx.getPrice();
            BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;

            if (tx.getTransactionType() == TransactionType.BUY) {
                BigDecimal cost = qty.multiply(price).add(commission);
                totalBuyCost   = totalBuyCost.add(cost);
                totalBuyQty    = totalBuyQty.add(qty);
                totalQuantity  = totalQuantity.add(qty);
            } else {
                totalQuantity = totalQuantity.subtract(qty);
            }
        }

        BigDecimal averageCost() {
            if (totalBuyQty.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return totalBuyCost.divide(totalBuyQty, 8, RoundingMode.HALF_UP);
        }
    }
}
