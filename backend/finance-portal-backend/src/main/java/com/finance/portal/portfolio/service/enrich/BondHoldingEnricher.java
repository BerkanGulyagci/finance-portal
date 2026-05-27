package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.MONEY_SCALE;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.applyMasFromCloses;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.marketValue;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.profitLoss;

/**
 * BOND holding zenginleştirmesi. İki tür tahvil var:
 * <ul>
 *   <li><b>Eurobond</b> (Hazine dış borç) — sembol HMB ISIN listesinde var; Business Insider'dan
 *       fiyat (kote para birimi) + TCMB canlı kuru ile TL hesabı (Model 1).</li>
 *   <li><b>EVDS bond</b> (iç piyasa) — TCMB EVDS indicator/historical serisi.</li>
 * </ul>
 *
 * <p>Branch kararı symbol'un HMB ISIN listesinde olmasına göre verilir; davranış aynen
 * {@code PortfolioHoldingMarketEnricher} eski kodundan taşındı (characterization-test-driven).
 */
@Component
public class BondHoldingEnricher {

    private static final Logger log = LoggerFactory.getLogger(BondHoldingEnricher.class);

    private final EvdsBondService evdsBondService;
    private final EurobondService eurobondService;

    public BondHoldingEnricher(EvdsBondService evdsBondService, EurobondService eurobondService) {
        this.evdsBondService = evdsBondService;
        this.eurobondService = eurobondService;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        String code = holding.getSymbol() != null
                ? holding.getSymbol().trim().toUpperCase(Locale.ROOT)
                : "";
        if (eurobondService.currentIsins().contains(code)) {
            enrichEurobond(holding, code);
        } else {
            enrichEvdsBond(holding);
        }
    }

    /** EVDS (TCMB) iç piyasa tahvili: indicator + history serisi → mv/pl/52w/MA. */
    private void enrichEvdsBond(PortfolioHoldingResponse holding) {
        String code = holding.getSymbol() != null ? holding.getSymbol().trim() : "";
        EvdsBondInstrument bond = evdsBondService.getEvdsBondDetail(code);
        BigDecimal price = bond.getIndicatorValue();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Bond EVDS indicator unavailable for: " + code);
        }

        BigDecimal mv = marketValue(price, holding.getTotalQuantity());
        BigDecimal pl = profitLoss(mv, holding.getTotalCost());

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
                    holding.setMa20(PortfolioMovingAverage.simpleMa(closes, 20));
                    holding.setMa50(PortfolioMovingAverage.simpleMa(closes, 50));
                }
            }
        } catch (Exception e) {
            log.debug("Bond history / MA unavailable for {}: {}", code, e.getMessage());
        }
    }

    /**
     * Eurobond (Hazine dış borç) — TL hesaplama (Model 1: TL maliyet, canlı kur).
     * Fiyat/künye Business Insider'dan; kote (USD/EUR/JPY) canlı TCMB satış kuruyla TL'ye çevrilir.
     * Maliyet kullanıcı tarafından TL girildiği için K/Z hem tahvil hem kur hareketini içerir.
     * 52w/MA serisi de aynı (güncel) kurla TL'ye çevrilir (altın/emtia ile aynı yaklaşım).
     */
    private void enrichEurobond(PortfolioHoldingResponse holding, String isin) {
        EurobondDetail d = eurobondService.detail(isin);
        if (d == null || d.getLastPriceTry() == null) {
            holding.setCurrency("TRY");
            holding.setName(d != null && d.getName() != null ? d.getName() : isin);
            return;
        }
        BigDecimal priceTry = d.getLastPriceTry();
        BigDecimal qty = holding.getTotalQuantity() != null ? holding.getTotalQuantity() : BigDecimal.ZERO;
        BigDecimal mv = priceTry.multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal cost = holding.getTotalCost() != null ? holding.getTotalCost() : BigDecimal.ZERO;
        BigDecimal pl = mv.subtract(cost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(priceTry);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setCurrency("TRY");
        holding.setName(d.getName() != null ? d.getName() : isin);
        holding.setChangePercent(d.getChangePercent());
        holding.setAsOf(LocalDateTime.now());

        try {
            BigDecimal rate = d.getFxRate() != null ? d.getFxRate() : BigDecimal.ONE;
            List<BigDecimal> closes = eurobondService.chart(isin, "1Y").stream()
                    .map(EurobondChartPoint::close).filter(Objects::nonNull)
                    .map(c -> c.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                    .collect(Collectors.toList());
            if (!closes.isEmpty()) {
                holding.setFiftyTwoWeekHigh(closes.stream().max(BigDecimal::compareTo).orElse(null));
                holding.setFiftyTwoWeekLow(closes.stream().min(BigDecimal::compareTo).orElse(null));
                applyMasFromCloses(holding, closes);
            }
        } catch (Exception e) {
            log.debug("Eurobond 52w/MA alınamadı {}: {}", isin, e.getMessage());
        }
    }
}
