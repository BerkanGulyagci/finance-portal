package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * İşlem listesinden açık pozisyonları (holdings) hesaplar ve canlı fiyat zenginleştirmesi uygular.
 */
@Component
public class PortfolioHoldingsBuilder {

    private static final int PRICE_SCALE = 8;
    private static final int MONEY_SCALE = 4;
    private static final int FUND_MONEY_SCALE = 8;

    private final HoldingMarketEnrichmentPort holdingMarketEnrichment;

    public PortfolioHoldingsBuilder(HoldingMarketEnrichmentPort holdingMarketEnrichment) {
        this.holdingMarketEnrichment = holdingMarketEnrichment;
    }

    public List<PortfolioHoldingResponse> build(List<PortfolioTransaction> transactions) {
        return buildWithClosed(transactions).holdings;
    }

    /**
     * {@link #build(List)} ile aynı sonucu döndürür ama tamamen kapatılmış pozisyonların
     * (openQuantity ≤ 0) realizedGainLoss toplamını da sembol×assetType bazlı sezgisel
     * para biriminde ayrıca verir. Açık holding listesi davranışı değişmez — kapatılmış
     * pozisyonlar yine sonuç listesinde gösterilmez.
     */
    public BuildResult buildWithClosed(List<PortfolioTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return new BuildResult(new ArrayList<>(), List.of());
        }

        Map<String, List<PortfolioTransaction>> byKey = new LinkedHashMap<>();
        for (PortfolioTransaction tx : transactions) {
            String keyPart = groupingKeyForTransaction(tx);
            String key = keyPart + "::" + tx.getAssetType().name();
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
        }

        List<PortfolioHoldingResponse> result = new ArrayList<>();
        List<ClosedPositionRealized> closed = new ArrayList<>();

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
                // Tamamen kapatılmış pozisyon: holding satırı oluşturmuyoruz ama
                // realizedGainLossSum portföy toplamına dahil edilmek üzere ayrı dönülür.
                if (acc.anySell && acc.realizedGainLossSum.signum() != 0) {
                    closed.add(new ClosedPositionRealized(
                            acc.symbol, acc.assetType,
                            acc.realizedGainLossSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                            guessNativeCurrency(acc.symbol, acc.assetType)));
                }
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
            holding.setOpenCostLots(acc.snapshotLots());

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

            holdingMarketEnrichment.enrich(holding);
            result.add(holding);
        }

        return new BuildResult(result, closed);
    }

    /**
     * {@link #buildWithClosed(List)} dönüş tipi. {@code holdings} eski davranışla aynı
     * (yalnızca açık pozisyonlar); {@code closedRealized} tamamen kapatılmış pozisyonlar
     * için sembol×assetType bazında gerçekleşmiş K/Z (varlık para biriminde) listesidir.
     */
    public record BuildResult(
            List<PortfolioHoldingResponse> holdings,
            List<ClosedPositionRealized> closedRealized
    ) {}

    /** Kapatılmış pozisyonun realized K/Z özetı — para birimi varlığa bağlıdır (örn. STOCK.IS → TRY). */
    public record ClosedPositionRealized(
            String symbol,
            AssetType assetType,
            BigDecimal realizedGainLoss,
            String currency
    ) {}

    /**
     * Kapanmış pozisyonda canlı enricher çalışmadığı için para birimini sembol/türden
     * tahminliyoruz. STOCK ".IS" eki TRY, diğerleri USD; diğer tüm tipler TRY (uygulama
     * kuralı: kripto/fon/altın/emtia/eurobond stored TL).
     */
    private static String guessNativeCurrency(String symbol, AssetType assetType) {
        if (assetType == AssetType.STOCK) {
            return symbol != null && symbol.toUpperCase().endsWith(".IS") ? "TRY" : "USD";
        }
        return "TRY";
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

    private static class HoldingAccumulator {

        final String symbol;
        final AssetType assetType;

        BigDecimal openQuantity = BigDecimal.ZERO;
        BigDecimal openCostBasis = BigDecimal.ZERO;

        BigDecimal realizedGainLossSum = BigDecimal.ZERO;
        BigDecimal totalSoldCostBasis = BigDecimal.ZERO;
        boolean anySell = false;

        LocalDateTime firstBuyDate = null;

        /**
         * Her BUY için (alış tarihi, kalan maliyet katkısı) mutable çift. SELL anında pre-sell
         * openQty oranında her lot küçültülür → average-cost methoduyla birebir uyumlu
         * (Σ lot.cost = openCostBasis invariant'ı korunur).
         */
        final List<MutableLot> lots = new ArrayList<>();

        HoldingAccumulator(String symbol, AssetType assetType) {
            this.symbol = symbol;
            this.assetType = assetType;
        }

        void apply(PortfolioTransaction tx) {
            BigDecimal qty = tx.getQuantity();
            BigDecimal price = tx.getPrice();
            BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;
            LocalDateTime txDate = tx.getTransactionDate();

            if (tx.getTransactionType() == TransactionType.BUY) {
                BigDecimal cost = qty.multiply(price).add(commission);
                openCostBasis = openCostBasis.add(cost);
                openQuantity = openQuantity.add(qty);

                lots.add(new MutableLot(txDate != null ? txDate.toLocalDate() : null, cost));

                if (txDate != null && (firstBuyDate == null || txDate.isBefore(firstBuyDate))) {
                    firstBuyDate = txDate;
                }
            } else {
                if (openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException(
                            "SELL with non-positive open quantity for " + symbol + " " + assetType);
                }

                BigDecimal preSellQty = openQuantity;

                BigDecimal soldCostBasis;
                boolean fullClose = qty.compareTo(openQuantity) >= 0;
                if (fullClose) {
                    soldCostBasis = openCostBasis;
                } else {
                    BigDecimal unitCost = openCostBasis.divide(openQuantity, 12, RoundingMode.HALF_UP);
                    soldCostBasis = qty.multiply(unitCost);
                }

                BigDecimal proceeds = qty.multiply(price).subtract(commission);
                BigDecimal pnlSlice = proceeds.subtract(soldCostBasis);

                realizedGainLossSum = realizedGainLossSum.add(pnlSlice);
                totalSoldCostBasis = totalSoldCostBasis.add(soldCostBasis);
                anySell = true;

                openCostBasis = openCostBasis.subtract(soldCostBasis);
                openQuantity = openQuantity.subtract(qty);

                // Lotları orantısal küçült: tam kapanışta hepsi sıfırlanır; aksi halde
                // remainingFraction = (preSellQty - qty) / preSellQty ile çarpılır.
                if (fullClose) {
                    lots.clear();
                } else {
                    BigDecimal remainingFraction = preSellQty.subtract(qty)
                            .divide(preSellQty, 12, RoundingMode.HALF_UP);
                    for (MutableLot lot : lots) {
                        lot.cost = lot.cost.multiply(remainingFraction);
                    }
                }
            }
        }

        BigDecimal averageOpenCost() {
            if (openQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return openCostBasis.divide(openQuantity, 8, RoundingMode.HALF_UP);
        }

        /** Lot listesini DTO record'larına dönüştürür (sıfır maliyetliler atılır). */
        List<PortfolioHoldingResponse.CostLot> snapshotLots() {
            List<PortfolioHoldingResponse.CostLot> out = new ArrayList<>(lots.size());
            for (MutableLot lot : lots) {
                if (lot.cost == null || lot.cost.signum() <= 0 || lot.buyDate == null) {
                    continue;
                }
                out.add(new PortfolioHoldingResponse.CostLot(lot.buyDate,
                        lot.cost.setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
            }
            return out;
        }
    }

    /** Internal mutable lot — accumulator'da SELL oranında küçültülebilsin diye. */
    private static class MutableLot {
        final java.time.LocalDate buyDate;
        BigDecimal cost;
        MutableLot(java.time.LocalDate buyDate, BigDecimal cost) {
            this.buyDate = buyDate;
            this.cost = cost;
        }
    }
}
