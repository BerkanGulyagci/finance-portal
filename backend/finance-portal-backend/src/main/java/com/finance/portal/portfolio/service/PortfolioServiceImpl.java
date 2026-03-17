package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.AssetPriceQueryService;
import com.finance.portal.market.application.AssetPriceSnapshot;
import com.finance.portal.portfolio.domain.Portfolio;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.dto.AddTransactionRequest;
import com.finance.portal.portfolio.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.dto.PortfolioResponse;
import com.finance.portal.portfolio.dto.PortfolioTransactionResponse;
import com.finance.portal.portfolio.repository.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final AssetPriceQueryService assetPriceQueryService;

    public PortfolioServiceImpl(PortfolioRepository portfolioRepository,
                                AssetPriceQueryService assetPriceQueryService) {
        this.portfolioRepository = portfolioRepository;
        this.assetPriceQueryService = assetPriceQueryService;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

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

        Portfolio portfolio = new Portfolio();
        portfolio.setUserId(userId);
        portfolio.setName(name);
        portfolio.setDescription(request.getDescription());
        portfolio.setCurrency(currency);

        portfolio = portfolioRepository.save(portfolio);
        log.debug("Created portfolio id={} for userId={}", portfolio.getId(), userId);

        return toPortfolioResponse(portfolio);
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

        return toPortfolioResponse(portfolio);
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolioById(String userId, UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Portfolio not found: id=" + portfolioId + " userId=" + userId));
        return toPortfolioResponse(portfolio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioResponse> getUserPortfolios(String userId) {
        return portfolioRepository.findByUserId(userId).stream()
                .map(this::toPortfolioResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private PortfolioResponse toPortfolioResponse(Portfolio portfolio) {
        List<PortfolioTransactionResponse> txResponses = portfolio.getTransactions().stream()
                .sorted(java.util.Comparator
                        .comparing(PortfolioTransaction::getTransactionDate).reversed()
                        .thenComparing(PortfolioTransaction::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
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
        response.setCreatedAt(portfolio.getCreatedAt());
        response.setUpdatedAt(portfolio.getUpdatedAt());
        response.setTransactions(txResponses);
        response.setHoldings(holdings);
        response.setTotalCost(totalCost);
        response.setTotalMarketValue(totalMarketValue);
        response.setTotalProfitLoss(totalProfitLoss);
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

    // -------------------------------------------------------------------------
    // Holding aggregation
    // -------------------------------------------------------------------------

    /**
     * Transaction listesinden holding'leri türetir.
     * <p>
     * Her (symbol, assetType) çifti için:
     * - BUY: quantity ve weighted cost biriktirilir
     * - SELL: quantity düşülür
     * - averageCost = sadece BUY işlemlerinin weighted average'ı
     * - totalCost   = averageCost * totalQuantity
     * - totalQuantity <= 0 olan holding'ler sonuca dahil edilmez
     */
    private List<PortfolioHoldingResponse> buildHoldings(List<PortfolioTransaction> transactions) {
        // key: "SYMBOL::ASSET_TYPE"
        Map<String, HoldingAccumulator> accMap = new LinkedHashMap<>();

        for (PortfolioTransaction tx : transactions) {
            String key = tx.getSymbol() + "::" + tx.getAssetType().name();
            accMap.computeIfAbsent(key, k -> new HoldingAccumulator(tx.getSymbol(), tx.getAssetType()));
            accMap.get(key).apply(tx);
        }

        List<PortfolioHoldingResponse> result = new ArrayList<>();

        for (HoldingAccumulator acc : accMap.values()) {
            if (acc.totalQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // pozisyon kapalı
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

    /**
     * Canlı fiyat bilgisini holding'e ekler.
     * Desteklenmeyen asset type veya API hatası durumunda market alanları null bırakılır;
     * tüm portföy response'u etkilenmez.
     */
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

    // -------------------------------------------------------------------------
    // SELL validation
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Symbol normalization
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Inner accumulator
    // -------------------------------------------------------------------------

    private static class HoldingAccumulator {

        final String symbol;
        final AssetType assetType;

        BigDecimal totalQuantity  = BigDecimal.ZERO;
        BigDecimal totalBuyCost   = BigDecimal.ZERO; // Σ (qty * price + commission) for BUY
        BigDecimal totalBuyQty    = BigDecimal.ZERO; // Σ qty for BUY

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

        /** Weighted average cost hesabı sadece BUY işlemlerinden yapılır. */
        BigDecimal averageCost() {
            if (totalBuyQty.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return totalBuyCost.divide(totalBuyQty, 8, RoundingMode.HALF_UP);
        }
    }
}
