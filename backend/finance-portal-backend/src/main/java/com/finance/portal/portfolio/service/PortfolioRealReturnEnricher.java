package com.finance.portal.portfolio.service;

import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.economy.model.EconomySeriesPoint;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.presentation.dto.PortfolioResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Portföy pozisyonlarına enflasyona göre düzeltilmiş (reel) getiri alanlarını ekler.
 *
 * <p>İki çerçeve hesaplanır:
 * <ul>
 *   <li><b>Kendi para birimi</b> ({@code realProfitLoss} vb.): pozisyon kendi para biriminin
 *       enflasyonuyla arındırılır — TL → TÜFE, USD → ABD CPI (FRED). "Bu varlık dolar/lira
 *       bazında kendi enflasyonunu yendi mi?"</li>
 *   <li><b>TL alım gücü</b> ({@code realProfitLossTry} vb.): tüm tutarlar güncel kurla TL'ye
 *       çevrilip TÜFE ile arındırılır. "Türk yatırımcı olarak alım gücüm gerçekte arttı mı?"
 *       Portföy toplamı ({@code totalRealProfitLoss}) bu çerçeveden, TÜM pozisyonlar üzerinden
 *       hesaplanır (boyut: TL).</li>
 * </ul>
 *
 * <p>TL-only portföylerde iki çerçeve aynıdır ve toplam, önceki TÜFE-only davranışıyla birebir
 * aynı kalır (kur çarpanı 1). İlk alış tarihi / değeri eksikse pozisyon atlanır.
 */
@Component
public class PortfolioRealReturnEnricher {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRealReturnEnricher.class);

    private static final int MONEY_SCALE = 2;
    private static final int PCT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final String SRC_TUFE = "TÜFE";
    private static final String SRC_US_CPI = "ABD TÜFE";

    private final InflationDeflatorService deflator;
    private final PortfolioCurrencyConverter currencyConverter;

    public PortfolioRealReturnEnricher(InflationDeflatorService deflator,
                                       PortfolioCurrencyConverter currencyConverter) {
        this.deflator = deflator;
        this.currencyConverter = currencyConverter;
    }

    /**
     * Yanıttaki tüm holding'lere reel getiri alanlarını ve portföy reel toplamlarını uygular.
     * Holding'ler bu noktada canlı fiyatla zenginleştirilmiş ({@code marketValue} dolu) olmalıdır.
     */
    @WithSpan("PortfolioRealReturn.apply")
    public void apply(PortfolioResponse response) {
        if (response == null) {
            return;
        }
        List<PortfolioHoldingResponse> holdings = response.getHoldings();
        if (holdings == null || holdings.isEmpty()) {
            return;
        }
        Span.current().setAttribute("portfolio.holdings", holdings.size());

        final List<EconomySeriesPoint> tufe = safeSeries(deflator::tufeSeries, "TÜFE");
        final List<EconomySeriesPoint> usCpi = safeSeries(deflator::usCpiSeries, "ABD CPI");
        // En azından TÜFE yoksa reel getiri hesaplanamaz.
        if (tufe == null || tufe.isEmpty()) {
            clearAll(holdings, response);
            return;
        }

        BigDecimal sumRealCostTl = BigDecimal.ZERO;
        BigDecimal sumRealPlTl = BigDecimal.ZERO;
        boolean anyTl = false;

        for (PortfolioHoldingResponse h : holdings) {
            clear(h);

            // FUTURE (VİOP) pozisyonları: lot bazlı enflasyon doğru değil (kaldıraçlı + günlük
            // mark-to-market). Bunun yerine yatırılan teminat (viopMarginPosted) tek bir "buy
            // date" çapasından bugüne TÜFE faktörüyle şişirilip reel teminat olarak alınır;
            // marketValue (canlı M2M) ile farkı reel K/Z'yi verir.
            if (h.getAssetType() == com.finance.portal.common.domain.AssetType.FUTURE) {
                BigDecimal margin = h.getViopMarginPosted();
                if (margin == null) margin = h.getTotalCost();
                BigDecimal mv = h.getMarketValue();
                if (margin == null || mv == null || margin.signum() <= 0) continue;

                LocalDate buyDate = h.getFirstBuyDate() != null ? h.getFirstBuyDate().toLocalDate() : null;
                // Reel K/Z: SON YAYINLANAN TÜFE'yi kullan (ekstrapolasyon YOK). TIPS/enflasyon-endeksli
                // enstrüman standardı: yayınlanmamış günler için endeks ileri taşınmaz, son bilinen
                // (lag'li) endeks esas alınır. cumulativeFactor(series, from) = latest/base. (Günlük
                // performans grafiği ayrı cumulativeFactor(from,to=p.date) çağrısıyla ekstrapole eder.)
                Optional<BigDecimal> fOpt = deflator.cumulativeFactor(tufe, buyDate);
                BigDecimal f = fOpt.orElse(BigDecimal.ONE);

                BigDecimal realCost = margin.multiply(f);
                h.setRealProfitLoss(mv.subtract(realCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                h.setRealProfitLossPercent(pct(mv, realCost));
                if (fOpt.isPresent()) {
                    h.setInflationSincePercent(f.subtract(BigDecimal.ONE)
                            .multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP));
                    h.setInflationSource(SRC_TUFE);
                }

                String cur = h.getCurrency();
                BigDecimal costTl = currencyConverter.toTry(margin, cur);
                BigDecimal mvTl = currencyConverter.toTry(mv, cur);
                if (costTl != null && mvTl != null && costTl.signum() > 0) {
                    BigDecimal realCostTl = costTl.multiply(f);
                    h.setRealProfitLossTry(mvTl.subtract(realCostTl).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                    h.setRealProfitLossPercentTry(pct(mvTl, realCostTl));
                    if (fOpt.isPresent()) {
                        h.setInflationSinceTryPercent(f.subtract(BigDecimal.ONE)
                                .multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP));
                    }
                    sumRealCostTl = sumRealCostTl.add(realCostTl);
                    sumRealPlTl = sumRealPlTl.add(mvTl.subtract(realCostTl));
                    anyTl = true;
                }
                continue;
            }

            BigDecimal cost = h.getTotalCost();
            BigDecimal marketValue = h.getMarketValue();
            if (cost == null || marketValue == null || cost.signum() <= 0) continue;

            String cur = h.getCurrency();
            boolean isTry = isTryCurrency(cur);
            boolean isUsd = isUsdCurrency(cur);
            LocalDate buyDate = h.getFirstBuyDate() != null ? h.getFirstBuyDate().toLocalDate() : null;

            // BOND için kupon stok-tipi step-up: Reel K/Z hesabında "güncel değer" =
            // marketValue + ŞimdiyeKadarKiKuponlar. AddCouponIncomeRequest TL kupon tutarı
            // saklar → couponsTl her zaman TRY. Native frame'de yalnız TRY pozisyon için
            // doğrudan eklenir (USD eurobond gibi non-TRY native bond'larda kupon currency
            // ayrımı yok → native frame boost'u atlanır, TL frame'de doğru kalır).
            BigDecimal coupons = (h.getAssetType() == com.finance.portal.common.domain.AssetType.BOND
                    && h.getSumCouponIncome() != null)
                    ? h.getSumCouponIncome() : BigDecimal.ZERO;
            BigDecimal couponsNative = (coupons.signum() > 0 && isTry) ? coupons : BigDecimal.ZERO;

            // Çoklu BUY'da firstBuyDate kullanmak en eski tarihten TÜM cost'u şişirir →
            // gerçek dışı kayıp gösterir. Her açık BUY lotunu (SELL'lerce orantısal küçültülmüş
            // halde) kendi alış tarihinden bugüne ayrı faktörle çarpıp toplarız.
            List<PortfolioHoldingResponse.CostLot> lots = h.getOpenCostLots();

            // ── 1) Kendi para birimi çerçevesi (TL→TÜFE, USD→ABD CPI) — satır kolonları, kesin ──
            List<EconomySeriesPoint> nativeSeries = isTry ? tufe : (isUsd ? usCpi : null);
            String nativeSource = isTry ? SRC_TUFE : (isUsd ? SRC_US_CPI : null);
            if (nativeSeries != null && !nativeSeries.isEmpty()) {
                LotInflation natFrame = computeLotInflation(lots, cost, buyDate, nativeSeries);
                if (natFrame != null) {
                    BigDecimal mvAdj = marketValue.add(couponsNative);
                    h.setRealProfitLoss(mvAdj.subtract(natFrame.realCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                    h.setRealProfitLossPercent(pct(mvAdj, natFrame.realCost));
                    h.setInflationSincePercent(natFrame.weightedFactor.subtract(BigDecimal.ONE)
                            .multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP));
                    h.setInflationSource(nativeSource);
                }
            }

            // ── 2) TL alım gücü çerçevesi (her şey TL'ye çevrilip TÜFE) ────────────
            BigDecimal costTl = currencyConverter.toTry(cost, cur);
            BigDecimal mvTl = currencyConverter.toTry(marketValue, cur);
            if (costTl == null || mvTl == null || costTl.signum() <= 0) continue;

            // Kupon TL'de saklanıyor → TL frame'de doğrudan eklenir.
            BigDecimal mvTlAdj = mvTl.add(coupons);

            // TÜFE çerçevesinde lots'ı TL'ye çeviriyoruz (cost-bazında oran korunur).
            LotInflation tlFrame = computeLotInflation(lots, costTl, buyDate, tufe);

            // 2a) Satır kolonları: yalnız hesaplanabilen enflasyon faktörüyle (yeni alımda "–").
            if (tlFrame != null) {
                h.setRealProfitLossTry(mvTlAdj.subtract(tlFrame.realCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                h.setRealProfitLossPercentTry(pct(mvTlAdj, tlFrame.realCost));
                h.setInflationSinceTryPercent(tlFrame.weightedFactor.subtract(BigDecimal.ONE)
                        .multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP));
            }

            // 2b) Portföy toplamı: TÜM varlıklar dahil; enflasyon faktörü yoksa 1 (reel=nominal).
            // Böylece toplam Reel K/Z, nominal Açık K/Z ile AYNI varlık kümesini kapsar → karşılaştırılabilir.
            BigDecimal realCostTlTotal = tlFrame != null ? tlFrame.realCost : costTl;
            sumRealCostTl = sumRealCostTl.add(realCostTlTotal);
            sumRealPlTl = sumRealPlTl.add(mvTlAdj.subtract(realCostTlTotal));
            anyTl = true;
        }

        if (anyTl) {
            response.setTotalRealProfitLoss(sumRealPlTl.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            if (sumRealCostTl.signum() > 0) {
                response.setTotalRealProfitLossPercent(
                        sumRealPlTl.divide(sumRealCostTl, MathContext.DECIMAL64)
                                .multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP));
            }
        } else {
            response.setTotalRealProfitLoss(null);
            response.setTotalRealProfitLossPercent(null);
        }
    }

    /** Lot bazlı reel maliyet ve ağırlıklı enflasyon faktörü taşıyıcısı. */
    private static final class LotInflation {
        final BigDecimal realCost;
        final BigDecimal weightedFactor;  // Σ(cost_i × factor_i) / Σ cost_i
        LotInflation(BigDecimal realCost, BigDecimal weightedFactor) {
            this.realCost = realCost;
            this.weightedFactor = weightedFactor;
        }
    }

    /**
     * Lots boş veya null ise eski davranışa (firstBuyDate üzerinden tek faktör) düşer.
     * Aksi halde her lot için kendi alış tarihinden bugüne enflasyon faktörü hesaplanır,
     * realCost = Σ lot.cost × factor_lot şeklinde toplanır. Bir lot için faktör yoksa o lot
     * 1 (reel=nominal) ile geçer — toplam bozulmasın diye.
     * Geri dönüş null = TÜFE/CPI hiç hesaplanamadı (eski "factor yok → boşa geç" davranışı).
     */
    private LotInflation computeLotInflation(List<PortfolioHoldingResponse.CostLot> lots,
                                             BigDecimal totalCost,
                                             LocalDate fallbackBuyDate,
                                             List<EconomySeriesPoint> series) {
        if (lots == null || lots.isEmpty()) {
            return singleLotFallback(totalCost, fallbackBuyDate, series);
        }
        BigDecimal realCostSum = BigDecimal.ZERO;
        BigDecimal costSum = BigDecimal.ZERO;
        BigDecimal weightedFactorNum = BigDecimal.ZERO;
        boolean anyFactor = false;
        for (PortfolioHoldingResponse.CostLot lot : lots) {
            BigDecimal c = lot.cost();
            if (c == null || c.signum() <= 0 || lot.buyDate() == null) continue;
            costSum = costSum.add(c);
            // SON YAYINLANAN endeks (ekstrapolasyon yok) — TIPS standardı; bkz. yukarıdaki not.
            Optional<BigDecimal> f = deflator.cumulativeFactor(series, lot.buyDate());
            if (f.isPresent()) {
                anyFactor = true;
                realCostSum = realCostSum.add(c.multiply(f.get()));
                weightedFactorNum = weightedFactorNum.add(c.multiply(f.get()));
            } else {
                // Bu lot için faktör yok → reel=nominal (Σ tutar bozulmasın).
                realCostSum = realCostSum.add(c);
                weightedFactorNum = weightedFactorNum.add(c);
            }
        }
        // Lot path yetersizse (hiç geçerli lot yok, hiç faktör hesaplanamadı, vb.) FUTURE
        // branch'iyle aynı mantıkta single-lot fallback'e düş — böylece kısa-vadeli/yeni
        // pozisyonlarda da satır reel kolonları boş kalmaz.
        if (!anyFactor || costSum.signum() <= 0) {
            return singleLotFallback(totalCost, fallbackBuyDate, series);
        }
        BigDecimal weighted = weightedFactorNum.divide(costSum, MathContext.DECIMAL64);
        // realCost'u verilen toplam cost'la (kuruşçu yuvarlama farkı için) yeniden ölçekle
        // ki frame'ler arası tutarlılık bozulmasın.
        BigDecimal scaledRealCost = realCostSum
                .multiply(totalCost)
                .divide(costSum, MathContext.DECIMAL64);
        return new LotInflation(scaledRealCost, weighted);
    }

    /**
     * Lot yokken (veya hiçbir lot için faktör hesaplanamazken) tek lot gibi davran:
     * fallbackBuyDate'ten bugüne TÜFE/CPI faktörüyle totalCost'u şişir. Faktör yoksa
     * (çok eski / çok yeni tarih) reel=nominal say — toplam bozulmasın, FUTURE branch ile aynı.
     */
    private LotInflation singleLotFallback(BigDecimal totalCost,
                                           LocalDate fallbackBuyDate,
                                           List<EconomySeriesPoint> series) {
        if (totalCost == null || totalCost.signum() <= 0) return null;
        if (fallbackBuyDate == null) return null;
        // SON YAYINLANAN endeks (ekstrapolasyon yok) — TIPS standardı; bkz. yukarıdaki not.
        BigDecimal f = deflator.cumulativeFactor(series, fallbackBuyDate).orElse(BigDecimal.ONE);
        return new LotInflation(totalCost.multiply(f), f);
    }

    /** (mv / base − 1) × 100, yüzde olarak. */
    private static BigDecimal pct(BigDecimal mv, BigDecimal base) {
        return mv.divide(base, MathContext.DECIMAL64)
                .subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private interface SeriesSupplier {
        List<EconomySeriesPoint> get();
    }

    private static List<EconomySeriesPoint> safeSeries(SeriesSupplier supplier, String label) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("[RealReturn] {} serisi alınamadı: {}", label, e.getMessage());
            return List.of();
        }
    }

    private static void clearAll(List<PortfolioHoldingResponse> holdings, PortfolioResponse response) {
        for (PortfolioHoldingResponse h : holdings) clear(h);
        response.setTotalRealProfitLoss(null);
        response.setTotalRealProfitLossPercent(null);
    }

    /** Cache'ten gelmiş eski değerler kalmasın diye tüm reel alanları sıfırla. */
    private static void clear(PortfolioHoldingResponse h) {
        h.setRealProfitLoss(null);
        h.setRealProfitLossPercent(null);
        h.setInflationSincePercent(null);
        h.setInflationSource(null);
        h.setRealProfitLossTry(null);
        h.setRealProfitLossPercentTry(null);
        h.setInflationSinceTryPercent(null);
    }

    private static boolean isTryCurrency(String currency) {
        return currency == null
                || "TRY".equalsIgnoreCase(currency)
                || "TL".equalsIgnoreCase(currency);
    }

    private static boolean isUsdCurrency(String currency) {
        if (currency == null) return false;
        String c = currency.trim().toUpperCase(Locale.ROOT);
        return "USD".equals(c) || "USDT".equals(c) || "$".equals(c);
    }
}
