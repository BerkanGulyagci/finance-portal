package com.finance.portal.portfolio.service;

import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.PortfolioType;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.domain.WatchlistItem;
import com.finance.portal.portfolio.application.performance.PortfolioPerformanceResult;
import com.finance.portal.portfolio.presentation.dto.AddTransactionRequest;
import com.finance.portal.portfolio.presentation.dto.AddWatchlistItemRequest;
import com.finance.portal.portfolio.presentation.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioTransactionResponse;
import com.finance.portal.portfolio.presentation.dto.UpdatePortfolioRequest;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.application.port.PortfolioCachePort;
import com.finance.portal.portfolio.application.port.PortfolioPersistencePort;
import com.finance.portal.portfolio.application.port.WatchlistMarketEnrichmentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PortfolioServiceImpl implements PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioServiceImpl.class);
    private static final int MONEY_SCALE = 4;

    private final PortfolioPersistencePort portfolioPersistence;
    private final PortfolioCachePort portfolioCache;
    private final ViopService viopService;
    private final WatchlistMarketEnrichmentPort watchlistMarketEnrichment;
    private final PortfolioHoldingsBuilder holdingsBuilder;
    private final HoldingMarketEnrichmentPort holdingMarketEnrichment;
    private final PortfolioPerformanceService portfolioPerformanceService;
    private final CentralBusinessLogService centralBusinessLogService;

    public PortfolioServiceImpl(PortfolioPersistencePort portfolioPersistence,
                                PortfolioCachePort portfolioCache,
                                ViopService viopService,
                                WatchlistMarketEnrichmentPort watchlistMarketEnrichment,
                                PortfolioHoldingsBuilder holdingsBuilder,
                                HoldingMarketEnrichmentPort holdingMarketEnrichment,
                                PortfolioPerformanceService portfolioPerformanceService,
                                CentralBusinessLogService centralBusinessLogService) {
        this.portfolioPersistence           = portfolioPersistence;
        this.portfolioCache                 = portfolioCache;
        this.viopService                    = viopService;
        this.watchlistMarketEnrichment       = watchlistMarketEnrichment;
        this.holdingsBuilder                = holdingsBuilder;
        this.holdingMarketEnrichment        = holdingMarketEnrichment;
        this.portfolioPerformanceService    = portfolioPerformanceService;
        this.centralBusinessLogService      = centralBusinessLogService;
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

        if (portfolioPersistence.existsByUserIdAndName(userId, name)) {
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

        portfolio = portfolioPersistence.savePortfolio(portfolio);
        log.debug("Created portfolio id={} type={} for userId={}", portfolio.getId(), portfolioType, userId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portfolioId", portfolio.getId().toString());
        metadata.put("portfolioType", portfolioType.name());
        metadata.put("currency", currency);

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_BUSINESS,
                BusinessLogSupport.EVENT_PORTFOLIO_CREATED,
                "INFO",
                "Portfolio created",
                "PORTFOLIO",
                portfolio.getId().toString(),
                BusinessLogSupport.ACTION_CREATE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                userId,
                PortfolioServiceImpl.class.getName()
        );

        if (portfolioType == PortfolioType.WATCHLIST) {
            Map<String, Object> watchlistMetadata = new HashMap<>();
            watchlistMetadata.put("portfolioId", portfolio.getId().toString());
            watchlistMetadata.put("portfolioType", portfolioType.name());

            centralBusinessLogService.publish(
                    BusinessLogSupport.CATEGORY_BUSINESS,
                    BusinessLogSupport.EVENT_WATCHLIST_PORTFOLIO_CREATED,
                    "INFO",
                    "Watchlist portfolio created",
                    "PORTFOLIO",
                    portfolio.getId().toString(),
                    BusinessLogSupport.ACTION_CREATE,
                    BusinessLogSupport.RESULT_SUCCESS,
                    watchlistMetadata,
                    userId,
                    PortfolioServiceImpl.class.getName()
            );
        }

        PortfolioResponse response = toPortfolioResponse(portfolio);
        portfolioCache.evictList(userId);
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

        Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
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
        portfolio = portfolioPersistence.savePortfolio(portfolio);
        log.debug("Added transaction symbol={} to portfolioId={}", normalizedSymbol, portfolioId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portfolioId", portfolioId.toString());
        metadata.put("transactionType", request.getTransactionType().name());
        metadata.put("assetType", request.getAssetType().name());
        metadata.put("symbol", normalizedSymbol);
        metadata.put("quantity", request.getQuantity());
        metadata.put("price", request.getPrice());
        metadata.put("transactionDate", request.getTransactionDate() != null
                ? request.getTransactionDate().toString() : null);

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_BUSINESS,
                BusinessLogSupport.EVENT_TRANSACTION_ADDED,
                "INFO",
                "Transaction added",
                "PORTFOLIO",
                portfolioId.toString(),
                BusinessLogSupport.ACTION_CREATE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                userId,
                PortfolioServiceImpl.class.getName()
        );

        PortfolioResponse response = toPortfolioResponse(portfolio);
        portfolioCache.evictListAndDetail(userId, portfolioId);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolioById(String userId, UUID portfolioId) {
        PortfolioResponse response = portfolioCache.getPortfolioDetail(userId, portfolioId)
                .orElseGet(() -> {
                    Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Portfolio not found: id=" + portfolioId + " userId=" + userId));
                    return toPortfolioResponse(portfolio);
                });
        // Redis'teki anlık snapshot fiyatları eskitir; her GET'te canlı fiyatları yeniden uygula
        // (FUTURE/VİOP gibi sonradan eklenen zenginleştirme de cache'te kalmış boş alanları giderir)
        if (response.getHoldings() != null) {
            for (PortfolioHoldingResponse h : response.getHoldings()) {
                holdingMarketEnrichment.enrich(h);
            }
        }
        // Önbelleğe yazmayı zenginleştirmeden SONRA yap — aksi halde Redis'te 52w/MA olmadan snapshot kalır
        portfolioCache.putPortfolioDetail(userId, portfolioId, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioPerformanceResult getPortfolioPerformance(
            String userId, UUID portfolioId, String range, String metric) {
        return portfolioPerformanceService.getPerformance(userId, portfolioId, range, metric);
    }

    @Override
    @Transactional
    public PortfolioResponse updatePortfolio(String userId, UUID portfolioId, UpdatePortfolioRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        String oldName = portfolio.getName();
        String oldDescription = portfolio.getDescription();

        String newName = request.getName().trim();
        if (!newName.equals(oldName)
                && portfolioPersistence.existsByUserIdAndName(userId, newName)) {
            throw new IllegalArgumentException(
                    "A portfolio with name '" + newName + "' already exists for this user");
        }

        portfolio.setName(newName);
        portfolio.setDescription(request.getDescription());
        portfolio = portfolioPersistence.savePortfolio(portfolio);
        log.debug("Updated portfolio id={} for userId={}", portfolioId, userId);

        List<String> changedFields = new ArrayList<>();
        if (!newName.equals(oldName)) {
            changedFields.add("name");
        }
        if (!Objects.equals(request.getDescription(), oldDescription)) {
            changedFields.add("description");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portfolioId", portfolioId.toString());
        metadata.put("portfolioType", portfolio.getPortfolioType().name());
        metadata.put("changedFields", changedFields);

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_BUSINESS,
                BusinessLogSupport.EVENT_PORTFOLIO_UPDATED,
                "INFO",
                "Portfolio updated",
                "PORTFOLIO",
                portfolioId.toString(),
                BusinessLogSupport.ACTION_UPDATE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                userId,
                PortfolioServiceImpl.class.getName()
        );

        PortfolioResponse response = toPortfolioResponse(portfolio);
        portfolioCache.evictListAndDetail(userId, portfolioId);
        return response;
    }

    @Override
    @Transactional
    public void deletePortfolio(String userId, UUID portfolioId) {
        Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));
        PortfolioType portfolioType = portfolio.getPortfolioType();
        portfolioPersistence.deletePortfolio(portfolio);
        log.debug("Deleted portfolio id={} for userId={}", portfolioId, userId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portfolioId", portfolioId.toString());
        metadata.put("portfolioType", portfolioType.name());

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_BUSINESS,
                BusinessLogSupport.EVENT_PORTFOLIO_DELETED,
                "WARN",
                "Portfolio deleted",
                "PORTFOLIO",
                portfolioId.toString(),
                BusinessLogSupport.ACTION_DELETE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                userId,
                PortfolioServiceImpl.class.getName()
        );

        portfolioCache.evictListDetailAndWatchlist(userId, portfolioId);
    }

    @Override
    @Transactional
    public PortfolioResponse deleteTransaction(String userId, UUID portfolioId, UUID transactionId) {
        portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        int deleted = portfolioPersistence.deleteTransactionByIdForPortfolioAndUser(
                transactionId, portfolioId, userId);
        if (deleted == 0) {
            throw new IllegalArgumentException("Transaction not found: id=" + transactionId);
        }

        log.debug("Deleted transaction id={} from portfolioId={}", transactionId, portfolioId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portfolioId", portfolioId.toString());
        metadata.put("transactionId", transactionId.toString());

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_BUSINESS,
                BusinessLogSupport.EVENT_TRANSACTION_DELETED,
                "WARN",
                "Transaction deleted",
                "PORTFOLIO",
                portfolioId.toString(),
                BusinessLogSupport.ACTION_DELETE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                userId,
                PortfolioServiceImpl.class.getName()
        );

        Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));
        PortfolioResponse response = toPortfolioResponse(portfolio);
        portfolioCache.evictListAndDetail(userId, portfolioId);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioResponse> getUserPortfolios(String userId) {
        return portfolioCache.getPortfolioList(userId).orElseGet(() -> {
            List<PortfolioResponse> list = portfolioPersistence.findByUserId(userId).stream()
                    .map(this::toPortfolioResponse)
                    .collect(Collectors.toList());
            portfolioCache.putPortfolioList(userId, list);
            return list;
        });
    }

    // ── WATCHLIST işlemleri ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistItemResponse> getWatchlistItems(String userId, UUID portfolioId) {
        return portfolioCache.getWatchlist(userId, portfolioId).orElseGet(() -> {
            Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Portfolio not found: id=" + portfolioId + " userId=" + userId));

            requireWatchlist(portfolio);

            List<WatchlistItemResponse> list = portfolioPersistence.findWatchlistByPortfolioId(portfolioId).stream()
                    .map(this::toWatchlistItemResponse)
                    .collect(Collectors.toList());
            portfolioCache.putWatchlist(userId, portfolioId, list);
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

        Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        requireWatchlist(portfolio);

        // Symbol normalize — CRYPTO lowercase, diğerleri uppercase
        String normalizedSymbol = normalizeSymbol(request.getAssetType(), request.getSymbol());

        // Duplicate kontrolü
        if (portfolioPersistence.existsWatchlistItem(
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

        item = portfolioPersistence.saveWatchlistItem(item);
        log.debug("Added watchlist item symbol={} assetType={} to portfolioId={}",
                normalizedSymbol, request.getAssetType(), portfolioId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portfolioId", portfolioId.toString());
        metadata.put("symbol", normalizedSymbol);
        metadata.put("assetType", request.getAssetType().name());

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_BUSINESS,
                BusinessLogSupport.EVENT_WATCHLIST_ITEM_ADDED,
                "INFO",
                "Watchlist item added",
                "PORTFOLIO",
                portfolioId.toString(),
                BusinessLogSupport.ACTION_CREATE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                userId,
                PortfolioServiceImpl.class.getName()
        );

        WatchlistItemResponse response = toWatchlistItemResponse(item);
        portfolioCache.evictListDetailAndWatchlist(userId, portfolioId);
        return response;
    }

    @Override
    @Transactional
    public void deleteWatchlistItem(String userId, UUID portfolioId, UUID itemId) {
        Portfolio portfolio = portfolioPersistence.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));

        requireWatchlist(portfolio);

        WatchlistItem item = portfolioPersistence.findWatchlistItemByIdAndPortfolioId(itemId, portfolioId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Watchlist item not found: id=" + itemId + " portfolioId=" + portfolioId));

        String symbol = item.getSymbol();
        portfolioPersistence.deleteWatchlistItemByIdAndPortfolioId(itemId, portfolioId);
        log.debug("Deleted watchlist item id={} from portfolioId={}", itemId, portfolioId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("portfolioId", portfolioId.toString());
        metadata.put("itemId", itemId.toString());
        metadata.put("symbol", symbol);

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_BUSINESS,
                BusinessLogSupport.EVENT_WATCHLIST_ITEM_REMOVED,
                "INFO",
                "Watchlist item removed",
                "PORTFOLIO",
                portfolioId.toString(),
                BusinessLogSupport.ACTION_DELETE,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                userId,
                PortfolioServiceImpl.class.getName()
        );

        portfolioCache.evictListDetailAndWatchlist(userId, portfolioId);
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

        List<PortfolioHoldingResponse> holdings = holdingsBuilder.build(portfolio.getTransactions());

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
            response.setWatchlistItemCount(portfolioPersistence.countWatchlistByPortfolioId(portfolio.getId()));
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
        watchlistMarketEnrichment.enrich(r);
        return r;
    }

    private void setWatchStartSnapshot(WatchlistItem item) {
        try {
            WatchlistItemResponse tmp = new WatchlistItemResponse();
            tmp.setAssetType(item.getAssetType());
            tmp.setSymbol(item.getSymbol());
            watchlistMarketEnrichment.enrich(tmp);
            item.setStartPrice(tmp.getLastPrice());
            item.setStartCurrency(tmp.getCurrency());
        } catch (Exception ex) {
            log.debug("Failed to set start snapshot for symbol={}: {}", item.getSymbol(), ex.getMessage());
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
}
