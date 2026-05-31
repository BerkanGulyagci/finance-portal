package com.finance.portal.portfolio.service;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.portfolio.domain.PortfolioTransaction;
import com.finance.portal.portfolio.domain.TransactionType;
import com.finance.portal.portfolio.application.port.HoldingMarketEnrichmentPort;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /**
     * BOND için fiyat "100 TL nominal üzerinden" kote edilir (TCMB konvansiyonu, dibs1.txt).
     * Maliyet ve gelir hesabında etkin fiyat = girilen_fiyat / 100. Eurobond (BI quote'u) da
     * aynı 100-üzeri ölçeği taşır.
     */
    private static final BigDecimal BOND_PAR_SCALE = new BigDecimal("100");

    private static final Logger log = LoggerFactory.getLogger(PortfolioHoldingsBuilder.class);

    private final HoldingMarketEnrichmentPort holdingMarketEnrichment;
    private final EvdsBondService evdsBondService;

    public PortfolioHoldingsBuilder(HoldingMarketEnrichmentPort holdingMarketEnrichment,
                                    EvdsBondService evdsBondService) {
        this.holdingMarketEnrichment = holdingMarketEnrichment;
        this.evdsBondService = evdsBondService;
    }

    /**
     * BOND alt-türü "altına dayalı senet" mi? Birim "adet/gram" → {@code /100} uygulanmaz.
     * EVDS detail lookup; symbol başına bir kez yapılır, hata durumunda false.
     */
    private boolean isGoldBondSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return false;
        try {
            EvdsBondInstrument b = evdsBondService.getEvdsBondDetail(symbol);
            return b != null && b.getCategory() == BondCategory.GOLD_INDEXED_BOND;
        } catch (Exception e) {
            log.debug("Gold bond lookup failed for {}: {}", symbol, e.getMessage());
            return false;
        }
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
            // BOND alt-türü altın senedi mi? Birim "adet/gram", /100 uygulanmaz.
            boolean isGoldBond = assetType == AssetType.BOND && isGoldBondSymbol(accSymbol);
            HoldingAccumulator acc = new HoldingAccumulator(accSymbol, assetType, isGoldBond);
            for (PortfolioTransaction tx : txs) {
                acc.apply(tx);
            }

            if (acc.openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                // Tamamen kapatılmış pozisyon: realizedGainLossSum portföy toplamına dahil edilir.
                if (acc.anySell && acc.realizedGainLossSum.signum() != 0) {
                    closed.add(new ClosedPositionRealized(
                            acc.symbol, acc.assetType,
                            acc.realizedGainLossSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                            guessNativeCurrency(acc.symbol, acc.assetType)));
                }
                // BOND için ek olarak: kapalı satırı holding listesinde de göster (kullanıcı
                // vade itfası sonrası realized K/Z ve toplam getiriyi görebilsin).
                // Sadece gerçek bir BUY geçmişi varsa emit et — yalnız kupon kaydı olan sentetik
                // durumlar listeyi kirletmesin.
                if (acc.assetType == AssetType.BOND && acc.anySell
                        && acc.totalBuyCostBasis.signum() > 0) {
                    PortfolioHoldingResponse closedRow = new PortfolioHoldingResponse();
                    closedRow.setSymbol(acc.symbol);
                    closedRow.setAssetType(AssetType.BOND);
                    closedRow.setClosed(true);
                    closedRow.setTotalQuantity(BigDecimal.ZERO);
                    closedRow.setTotalCost(BigDecimal.ZERO);
                    closedRow.setMarketValue(BigDecimal.ZERO);
                    closedRow.setProfitLoss(BigDecimal.ZERO);
                    closedRow.setCurrency("TRY");
                    closedRow.setFirstBuyDate(acc.firstBuyDate);
                    closedRow.setLastTransactionDate(txs.get(txs.size() - 1).getTransactionDate());
                    closedRow.setInitialCost(acc.totalBuyCostBasis.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                    closedRow.setRealizedGainLoss(acc.realizedGainLossSum.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                    if (acc.totalBuyCostBasis.compareTo(BigDecimal.ZERO) > 0) {
                        closedRow.setRealizedGainLossPercent(
                                acc.realizedGainLossSum
                                        .divide(acc.totalBuyCostBasis, 10, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(2, RoundingMode.HALF_UP));
                    }
                    result.add(closedRow);
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

            // VİOP: pozisyon yönü transaction'lardan kalıt — grouping key direction'ı içerdiği
            // için bir holding içindeki tüm txs zaten aynı yönde olur.
            if (acc.assetType == AssetType.FUTURE && !txs.isEmpty()) {
                String firstDir = txs.get(0).getDirection();
                String dir = (firstDir != null && !firstDir.isBlank())
                        ? firstDir.trim().toUpperCase() : "LONG";
                holding.setViopDirection(dir);
            }

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
            // LONG ve SHORT aynı sembolde AYRI holding olur — quantity'ler karışmasın
            // ve PnL doğru yönde hesaplansın. Eski (direction=null) transaction'lar LONG'a düşer
            // ve mevcut holdings davranışı aynı kalır.
            String dir = tx.getDirection();
            String dirNorm = (dir != null && !dir.isBlank()) ? dir.trim().toUpperCase() : "LONG";
            return ViopService.portfolioFutureGroupKey(tx.getSymbol()) + "::" + dirNorm;
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
        /** Altına dayalı BOND ise birim "adet/gram" — /100 ölçeği UYGULANMAZ. */
        final boolean isGoldBond;

        BigDecimal openQuantity = BigDecimal.ZERO;
        BigDecimal openCostBasis = BigDecimal.ZERO;

        BigDecimal realizedGainLossSum = BigDecimal.ZERO;
        BigDecimal totalSoldCostBasis = BigDecimal.ZERO;
        /**
         * Hiç SELL yapılmadan önce yatırılan toplam BUY maliyeti — kapalı pozisyon (özellikle BOND
         * vade itfası sonrası) için "Toplam yatırım vs realized" görüntüsünde kullanılır.
         * SELL ile azalmaz; sadece BUY ile büyür.
         */
        BigDecimal totalBuyCostBasis = BigDecimal.ZERO;
        boolean anySell = false;

        LocalDateTime firstBuyDate = null;

        /**
         * Her BUY için (alış tarihi, kalan maliyet katkısı) mutable çift. SELL anında pre-sell
         * openQty oranında her lot küçültülür → average-cost methoduyla birebir uyumlu
         * (Σ lot.cost = openCostBasis invariant'ı korunur).
         */
        final List<MutableLot> lots = new ArrayList<>();

        HoldingAccumulator(String symbol, AssetType assetType, boolean isGoldBond) {
            this.symbol = symbol;
            this.assetType = assetType;
            this.isGoldBond = isGoldBond;
        }

        void apply(PortfolioTransaction tx) {
            BigDecimal qty = tx.getQuantity();
            BigDecimal price = tx.getPrice();
            BigDecimal commission = tx.getCommission() != null ? tx.getCommission() : BigDecimal.ZERO;
            LocalDateTime txDate = tx.getTransactionDate();

            // COUPON_INCOME: pozisyondan bağımsız realized gelir kaydı.
            // Konvansiyon: quantity = TL kupon tutarı, price = 1; realized += qty × price = amount.
            if (tx.getTransactionType() == TransactionType.COUPON_INCOME) {
                BigDecimal income = qty.multiply(price);
                realizedGainLossSum = realizedGainLossSum.add(income);
                anySell = true; // realized alanları DTO'ya yansısın
                return;
            }

            // BOND için piyasa konvansiyonu "100 TL nominal üzerinden fiyat" — etkin fiyat = price/100.
            // İstisna: altın bond (adet/gram bazlı) /100 uygulanmaz.
            BigDecimal effectivePrice = (assetType == AssetType.BOND && !isGoldBond)
                    ? price.divide(BOND_PAR_SCALE, 12, RoundingMode.HALF_UP)
                    : price;

            if (tx.getTransactionType() == TransactionType.BUY) {
                BigDecimal cost = qty.multiply(effectivePrice).add(commission);
                openCostBasis = openCostBasis.add(cost);
                openQuantity = openQuantity.add(qty);
                totalBuyCostBasis = totalBuyCostBasis.add(cost);

                lots.add(new MutableLot(txDate != null ? txDate.toLocalDate() : null, cost, qty));

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

                BigDecimal proceeds = qty.multiply(effectivePrice).subtract(commission);
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
                        if (lot.qty != null) {
                            lot.qty = lot.qty.multiply(remainingFraction);
                        }
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
                        lot.cost.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                        lot.qty));
            }
            return out;
        }
    }

    /** Internal mutable lot — accumulator'da SELL oranında küçültülebilsin diye. */
    private static class MutableLot {
        final java.time.LocalDate buyDate;
        BigDecimal cost;
        BigDecimal qty;
        MutableLot(java.time.LocalDate buyDate, BigDecimal cost, BigDecimal qty) {
            this.buyDate = buyDate;
            this.cost = cost;
            this.qty = qty;
        }
    }
}
