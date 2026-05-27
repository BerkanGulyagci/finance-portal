package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioDateTimeParse;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
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
import java.util.Optional;
import java.util.TreeMap;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.MONEY_SCALE;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.PRICE_SCALE;

/**
 * FUTURE (VİOP) holding zenginleştirmesi. Akbank kontrat listesi (anlık fiyat + uzlaşma) +
 * İş Yatırım grafik (52w aralığı + günlük MA20/MA50) kombinasyonu.
 *
 * <p>Akbank cache geçici boş / eşleşme başarısız olsa bile grafik enrichment'ı çalışsın diye
 * grafik kısmı liste yolundan ayrılmıştır (önce grafik denenir, sonra liste). Kontrat ismi
 * Akbank kayıt adıyla farklıysa grafik bir kez daha kanonik isimle denenir.
 *
 * <p>VİOP'ta gerçek hacim verisi yok (sadece açık pozisyon), bu yüzden {@code volume} doldurulmaz.
 * Davranış {@code PortfolioHoldingMarketEnricher.enrichFutureHolding(...)} eski kodundan aynen taşındı.
 */
@Component
public class FutureHoldingEnricher {

    private static final Logger log = LoggerFactory.getLogger(FutureHoldingEnricher.class);

    private final ViopService viopService;
    private final ViopChartService viopChartService;

    public FutureHoldingEnricher(ViopService viopService, ViopChartService viopChartService) {
        this.viopService = viopService;
        this.viopChartService = viopChartService;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        String contractName = holding.getSymbol() != null ? holding.getSymbol().trim() : null;
        if (contractName == null || contractName.isBlank()) {
            return;
        }
        // Liste başarısız olsa bile (geçici boş cache vb.) 52w / MA kaybolmasın.
        applyViopYearChartMetrics(holding, contractName);

        ViopContractDetail d;
        try {
            Optional<ViopContract> match = viopService.findMatchingContract(contractName);
            if (match.isEmpty()) {
                log.debug("VIOP contracts list had no row matching holding symbol={}", contractName);
                return;
            }
            d = viopService.buildDetailDto(match.get());
        } catch (Exception ex) {
            log.warn("VIOP enrichment failed for holding symbol={}: {}", contractName, ex.getMessage());
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

        // Kontrat ismi Akbank kayıt adıyla farklıysa grafik enrichment'ı kanonik isimle bir kez daha dene.
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
}
