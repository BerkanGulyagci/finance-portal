package com.finance.portal.portfolio.service.performance;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.portfolio.application.performance.ExcludedPerformanceAsset;
import com.finance.portal.portfolio.application.performance.PortfolioPerformancePoint;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import com.finance.portal.portfolio.application.viop.valuation.ViopValuationService;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.infrastructure.market.PortfolioHistoricalPriceAdapter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/**
 * İşlem replay + günlük portföy değeri hesabı ({@link com.finance.portal.portfolio.service.PortfolioHoldingsBuilder} ile uyumlu maliyet mantığı).
 */
@Component
public class PortfolioPerformanceCalculator {

    private static final int MONEY_SCALE = 4;
    private static final int PCT_SCALE = 2;

    /** BOND için TCMB konvansiyonu: fiyat 100 TL nominal üzerinden — etkin = price/100. */
    private static final BigDecimal BOND_PAR_SCALE = new BigDecimal("100");

    private final EvdsBondService evdsBondService;
    private final ViopContractSpecRegistry viopSpecRegistry;
    private final ViopValuationService viopValuation;
    private final com.finance.portal.market.application.fx.port.TcmbFxHistoryPort fxHistoryPort;

    public PortfolioPerformanceCalculator(EvdsBondService evdsBondService,
                                          ViopContractSpecRegistry viopSpecRegistry,
                                          ViopValuationService viopValuation,
                                          com.finance.portal.market.application.fx.port.TcmbFxHistoryPort fxHistoryPort) {
        this.evdsBondService = evdsBondService;
        this.viopSpecRegistry = viopSpecRegistry;
        this.viopValuation = viopValuation;
        this.fxHistoryPort = fxHistoryPort;
    }

    /** İşlemler arasında USD-kote VİOP kontratı var mı? (varsa per-date FX serisi yüklenir.) */
    private boolean hasUsdViop(List<PortfolioTransaction> txs) {
        if (txs == null || viopSpecRegistry == null) return false;
        for (PortfolioTransaction tx : txs) {
            if (tx.getAssetType() == AssetType.FUTURE && tx.getSymbol() != null) {
                ViopContractSpec spec = viopSpecRegistry.resolveOrFallback(tx.getSymbol());
                if (spec != null && "USD".equalsIgnoreCase(spec.currency())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Per-unit kote (altın bonosu/kira sertifikası, 1 gram has altın)? /100 uygulanmaz. */
    public boolean isGoldBondSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        try {
            EvdsBondInstrument b = evdsBondService.getEvdsBondDetail(symbol);
            return b != null && b.getCategory() != null && b.getCategory().usesPerUnitNominalQuote();
        } catch (Exception e) {
            return false;
        }
    }

    public List<PortfolioPerformancePoint> calculate(
            List<PortfolioTransaction> transactions,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, NavigableMap<LocalDate, BigDecimal>> priceSeriesByPositionKey,
            Set<String> excludedPositionKeys,
            List<ExcludedPerformanceAsset> excludedAssetsOut) {

        List<PortfolioTransaction> sorted = new ArrayList<>(transactions != null ? transactions : List.of());
        sorted.sort(Comparator
                .comparing(PortfolioTransaction::getTransactionDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PortfolioTransaction::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        // Tarihsel davranış: caller'ın set'ini mutate eder (sembol fiyatsız ise eklenir),
        // PortfolioPerformanceServiceTest bu mutation'ı bekliyor. null güvenliği için fallback.
        Set<String> excludedKeys = excludedPositionKeys != null
                ? excludedPositionKeys : new HashSet<>();

        // USD-kote VİOP varsa, grafik günlerini O GÜNÜN USD/TRY kuruyla TL'ye çevirmek için
        // tarih bazlı kur serisini bir kez yükle (per-date FX; CommodityHistoricalTryConversion deseni).
        // Yoksa boş kalır, çevirim gerekmez (TRY kontratlar etkilenmez).
        final NavigableMap<LocalDate, BigDecimal> usdTryByDay =
                hasUsdViop(sorted)
                        ? com.finance.portal.portfolio.infrastructure.market.CommodityHistoricalTryConversion
                              .loadUsdTryHistory(fxHistoryPort, startDate, endDate)
                        : new java.util.TreeMap<>();

        Map<String, HoldingAccumulator> openPositions = new LinkedHashMap<>();
        List<PortfolioPerformancePoint> points = new ArrayList<>();

        // Bir varlığı, fiyatı bulunan ilk günden itibaren hesaba katmak için: tutulan günleri ve
        // gerçekten fiyatla katkı veren günleri ayrı izleriz. Hiç katkı vermeyen varlık sonda hariç tutulur.
        Set<String> heldKeys = new LinkedHashSet<>();
        Set<String> contributedKeys = new HashSet<>();
        Map<String, HoldingAccumulator> seenAccumulators = new LinkedHashMap<>();

        int txIndex = 0;
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            while (txIndex < sorted.size()) {
                PortfolioTransaction tx = sorted.get(txIndex);
                LocalDateTime txDt = tx.getTransactionDate();
                if (txDt == null || txDt.toLocalDate().isAfter(day)) {
                    break;
                }
                String key = positionKey(tx);
                if (!excludedKeys.contains(key)) {
                    final String dsym = displaySymbol(tx);
                    final boolean isGold = tx.getAssetType() == AssetType.BOND && isGoldBondSymbol(dsym);
                    if (tx.getAssetType() == AssetType.FUTURE) {
                        final String dir = tx.getDirection() != null && !tx.getDirection().isBlank()
                                ? tx.getDirection().trim().toUpperCase()
                                : "LONG";
                        final ViopContractSpec spec = viopSpecRegistry != null
                                ? viopSpecRegistry.resolveOrFallback(tx.getSymbol())
                                : ViopContractSpec.fallback(tx.getSymbol() != null ? tx.getSymbol() : "");
                        openPositions.computeIfAbsent(key, k -> new HoldingAccumulator(
                                key, dsym, tx.getAssetType(), false, dir, spec)).apply(tx);
                    } else {
                        openPositions.computeIfAbsent(key, k -> new HoldingAccumulator(
                                key, dsym, tx.getAssetType(), isGold, null, null)).apply(tx);
                    }
                    HoldingAccumulator acc = openPositions.get(key);
                    if (acc.openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        openPositions.remove(key);
                    }
                }
                txIndex++;
            }

            BigDecimal totalMarketValue = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;

            for (Map.Entry<String, HoldingAccumulator> e : openPositions.entrySet()) {
                String key = e.getKey();
                if (excludedKeys.contains(key)) {
                    continue;
                }
                HoldingAccumulator acc = e.getValue();
                if (acc.openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                heldKeys.add(key);
                seenAccumulators.putIfAbsent(key, acc);
                NavigableMap<LocalDate, BigDecimal> series = priceSeriesByPositionKey.get(key);
                BigDecimal unitPrice = priceOnDay(series, day);
                if (unitPrice == null) {
                    // O gün fiyat yok (ör. VİOP kontratının ilk işlem gününden önce) → bu günü atla
                    // ama pozisyonu DÜŞÜRME; veri başladığı gün hesaba katılsın.
                    continue;
                }
                contributedKeys.add(key);
                BigDecimal mv;
                BigDecimal costContribution;
                if (acc.assetType == AssetType.FUTURE && acc.viopSpec != null) {
                    // VİOP: marginPosted + pnl (notional değil) — portföy katkısı kaldıraçsız tutarlıdır.
                    BigDecimal avgEntry = acc.openCostBasis.divide(acc.openQuantity, 12, RoundingMode.HALF_UP);
                    // USD-kote kontratta O GÜNÜN USD/TRY kuruyla TL'ye çevir (per-date FX) — yoksa
                    // grafik ham USD değerinde düz gider, bugün enricher TL verince fırlardı. TRY → 1.
                    BigDecimal fxRate = "USD".equalsIgnoreCase(acc.viopSpec.currency())
                            ? com.finance.portal.portfolio.infrastructure.market.CommodityHistoricalTryConversion
                                  .resolveUsdTryRate(day, usdTryByDay, null)
                            : BigDecimal.ONE;
                    if (fxRate == null || fxRate.signum() <= 0) fxRate = BigDecimal.ONE;
                    BigDecimal marginPosted = viopValuation.marginPosted(acc.openQuantity, avgEntry, acc.viopSpec, fxRate);
                    BigDecimal pnl = viopValuation.pnl(acc.openQuantity, avgEntry, unitPrice, acc.viopSpec, acc.direction, fxRate);
                    mv = viopValuation.portfolioContribution(marginPosted, pnl)
                            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                    costContribution = marginPosted.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                } else {
                    // BOND için fiyat 100 nominal üzerinden — etkin = unitPrice/100.
                    // Altın bond istisnası: birim adet/gram → /100 uygulanmaz.
                    BigDecimal effectivePrice = (acc.assetType == AssetType.BOND && !acc.isGoldBond)
                            ? unitPrice.divide(BOND_PAR_SCALE, 12, RoundingMode.HALF_UP)
                            : unitPrice;
                    mv = effectivePrice.multiply(acc.openQuantity).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                    costContribution = acc.openCostBasis.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                }
                totalMarketValue = totalMarketValue.add(mv);
                totalCost = totalCost.add(costContribution);
            }

            BigDecimal profitLoss = totalMarketValue.subtract(totalCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal profitLossPercent = BigDecimal.ZERO.setScale(PCT_SCALE, RoundingMode.HALF_UP);
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                profitLossPercent = profitLoss
                        .divide(totalCost, 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(PCT_SCALE, RoundingMode.HALF_UP);
            }

            points.add(new PortfolioPerformancePoint(day, totalMarketValue, totalCost, profitLoss, profitLossPercent));
        }

        // Tüm dönem boyunca tutulduğu halde hiç fiyatı bulunamayan varlıkları "hariç tutuldu" işaretle.
        if (excludedAssetsOut != null) {
            for (String key : heldKeys) {
                if (!contributedKeys.contains(key) && !excludedKeys.contains(key)) {
                    HoldingAccumulator acc = seenAccumulators.get(key);
                    if (acc != null) {
                        markExcluded(excludedAssetsOut, excludedKeys, acc);
                    }
                }
            }
        }

        return points;
    }

    public static String positionKey(PortfolioTransaction tx) {
        if (tx.getAssetType() == AssetType.FUTURE) {
            String dn = tx.getDirection() != null && !tx.getDirection().isBlank()
                    ? tx.getDirection().trim().toUpperCase()
                    : "LONG";
            return ViopService.portfolioFutureGroupKey(tx.getSymbol()) + "::FUTURE::" + dn;
        }
        String symPart = tx.getSymbol() != null ? tx.getSymbol() : "";
        return symPart + "::" + tx.getAssetType().name();
    }

    public static String displaySymbol(PortfolioTransaction tx) {
        if (tx.getAssetType() == AssetType.FUTURE) {
            String n = ViopService.normalizeStoredFutureSymbol(tx.getSymbol());
            return n != null ? n : (tx.getSymbol() != null ? tx.getSymbol() : "");
        }
        return tx.getSymbol();
    }

    static BigDecimal priceOnDay(NavigableMap<LocalDate, BigDecimal> series, LocalDate day) {
        if (series == null || series.isEmpty()) {
            return null;
        }
        Map.Entry<LocalDate, BigDecimal> entry = series.floorEntry(day);
        if (entry == null || entry.getValue() == null) {
            return null;
        }
        BigDecimal price = entry.getValue();
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return price;
    }

    private static boolean markExcluded(List<ExcludedPerformanceAsset> out,
                                      Set<String> excludedKeys,
                                      HoldingAccumulator acc) {
        if (excludedKeys.add(acc.positionKey)) {
            out.add(new ExcludedPerformanceAsset(
                    acc.symbol,
                    acc.assetType,
                    PortfolioHistoricalPriceAdapter.exclusionReason(acc.assetType, acc.symbol)));
            return true;
        }
        return false;
    }

    static class HoldingAccumulator {

        final String positionKey;
        final String symbol;
        final AssetType assetType;
        final boolean isGoldBond;
        final String direction;
        final ViopContractSpec viopSpec;

        BigDecimal openQuantity = BigDecimal.ZERO;
        BigDecimal openCostBasis = BigDecimal.ZERO;

        HoldingAccumulator(String positionKey, String symbol, AssetType assetType, boolean isGoldBond,
                           String direction, ViopContractSpec viopSpec) {
            this.positionKey = positionKey;
            this.symbol = symbol;
            this.assetType = assetType;
            this.isGoldBond = isGoldBond;
            this.direction = direction;
            this.viopSpec = viopSpec;
        }

        void apply(PortfolioTransaction tx) {
            BigDecimal qty = tx.getQuantity();
            BigDecimal price = tx.getPrice();
            BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;

            // COUPON_INCOME: replay'de pozisyon değiştirmez (sadece realized gelir; performance MV'yi etkilemez).
            if (tx.getTransactionType() == TransactionType.COUPON_INCOME) {
                return;
            }

            // BOND: TCMB konvansiyonu — fiyat "100 TL nominal üzerinden".
            // Altın bond istisnası: birim adet/gram → /100 uygulanmaz.
            BigDecimal effectivePrice = (assetType == AssetType.BOND && !isGoldBond)
                    ? price.divide(BOND_PAR_SCALE, 12, RoundingMode.HALF_UP)
                    : price;

            if (tx.getTransactionType() == TransactionType.BUY) {
                BigDecimal cost = qty.multiply(effectivePrice).add(commission);
                openCostBasis = openCostBasis.add(cost);
                openQuantity = openQuantity.add(qty);
            } else {
                if (openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    return;
                }
                BigDecimal soldCostBasis;
                if (qty.compareTo(openQuantity) >= 0) {
                    soldCostBasis = openCostBasis;
                } else {
                    BigDecimal unitCost = openCostBasis.divide(openQuantity, 12, RoundingMode.HALF_UP);
                    soldCostBasis = qty.multiply(unitCost);
                }
                openCostBasis = openCostBasis.subtract(soldCostBasis);
                openQuantity = openQuantity.subtract(qty);
            }
        }
    }
}
