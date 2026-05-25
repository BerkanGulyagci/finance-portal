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

            BigDecimal cost = h.getTotalCost();
            BigDecimal marketValue = h.getMarketValue();
            if (cost == null || marketValue == null || cost.signum() <= 0) continue;

            String cur = h.getCurrency();
            boolean isTry = isTryCurrency(cur);
            boolean isUsd = isUsdCurrency(cur);
            LocalDate buyDate = h.getFirstBuyDate() != null ? h.getFirstBuyDate().toLocalDate() : null;

            // ── 1) Kendi para birimi çerçevesi (TL→TÜFE, USD→ABD CPI) — satır kolonları, kesin ──
            if (buyDate != null) {
                List<EconomySeriesPoint> nativeSeries = isTry ? tufe : (isUsd ? usCpi : null);
                String nativeSource = isTry ? SRC_TUFE : (isUsd ? SRC_US_CPI : null);
                if (nativeSeries != null && !nativeSeries.isEmpty()) {
                    Optional<BigDecimal> f = deflator.cumulativeFactor(nativeSeries, buyDate);
                    if (f.isPresent()) {
                        BigDecimal factor = f.get();
                        BigDecimal realCost = cost.multiply(factor);
                        h.setRealProfitLoss(marketValue.subtract(realCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                        h.setRealProfitLossPercent(pct(marketValue, realCost));
                        h.setInflationSincePercent(factor.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP));
                        h.setInflationSource(nativeSource);
                    }
                }
            }

            // ── 2) TL alım gücü çerçevesi (her şey TL'ye çevrilip TÜFE) ────────────
            BigDecimal costTl = currencyConverter.toTry(cost, cur);
            BigDecimal mvTl = currencyConverter.toTry(marketValue, cur);
            if (costTl == null || mvTl == null || costTl.signum() <= 0) continue;

            Optional<BigDecimal> ftOpt = buyDate != null
                    ? deflator.cumulativeFactor(tufe, buyDate)
                    : Optional.empty();

            // 2a) Satır kolonları: yalnız hesaplanabilen enflasyon faktörüyle (yeni alımda "–").
            if (ftOpt.isPresent()) {
                BigDecimal factor = ftOpt.get();
                BigDecimal realCostTl = costTl.multiply(factor);
                h.setRealProfitLossTry(mvTl.subtract(realCostTl).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
                h.setRealProfitLossPercentTry(pct(mvTl, realCostTl));
                h.setInflationSinceTryPercent(factor.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(PCT_SCALE, RoundingMode.HALF_UP));
            }

            // 2b) Portföy toplamı: TÜM varlıklar dahil; enflasyon faktörü yoksa 1 (reel=nominal).
            // Böylece toplam Reel K/Z, nominal Açık K/Z ile AYNI varlık kümesini kapsar → karşılaştırılabilir.
            BigDecimal totalFactor = ftOpt.orElse(BigDecimal.ONE);
            BigDecimal realCostTlTotal = costTl.multiply(totalFactor);
            sumRealCostTl = sumRealCostTl.add(realCostTlTotal);
            sumRealPlTl = sumRealPlTl.add(mvTl.subtract(realCostTlTotal));
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
