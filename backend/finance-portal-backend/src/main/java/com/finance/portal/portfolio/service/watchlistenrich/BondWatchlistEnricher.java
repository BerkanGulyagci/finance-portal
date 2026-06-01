package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.watchlistenrich.WatchlistTrendMath.applyMaAnd52w;

/**
 * BOND watchlist item zenginleştirmesi. Symbol HMB ISIN listesinde varsa Eurobond branch
 * (Business Insider kote × canlı TCMB kuru = TL); değilse EVDS DİBS branch.
 * Trend metrikleri her iki branch için de kendi 1Y serilerinden hesaplanır.
 *
 * <p>Daha önce {@code PortfolioWatchlistMarketEnricher.enrichBond + enrichEurobond + bondCloses1y} idi.
 */
@Component
public class BondWatchlistEnricher {

    private final EvdsBondService evdsBondService;
    private final EurobondService eurobondService;

    public BondWatchlistEnricher(EvdsBondService evdsBondService, EurobondService eurobondService) {
        this.evdsBondService = evdsBondService;
        this.eurobondService = eurobondService;
    }

    public void enrich(WatchlistItemResponse r, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        String upper = symbol.trim().toUpperCase(Locale.ROOT);
        if (eurobondService.currentIsins().contains(upper)) {
            enrichEurobond(r, upper);
            applyMaAnd52w(r, eurobondCloses1y(upper));
        } else {
            enrichEvds(r, symbol);
            applyMaAnd52w(r, evdsCloses1y(symbol));
        }
    }

    /** Eurobond izleme satırı — fiyat TL (kote × canlı kur); favori kartı ₺ ile gösterir. */
    private void enrichEurobond(WatchlistItemResponse r, String isin) {
        EurobondDetail d = eurobondService.detail(isin);
        r.setCurrency("TRY");
        if (d == null || d.getLastPriceTry() == null) {
            return;
        }
        r.setLastPrice(d.getLastPriceTry());
        r.setChangePercent(d.getChangePercent());
        r.setAsOf(LocalDateTime.now());
    }

    private void enrichEvds(WatchlistItemResponse r, String instrumentCode) {
        EvdsBondInstrument bond = evdsBondService.getEvdsBondDetail(instrumentCode);
        r.setCurrency("TRY");
        if (bond == null) {
            // Servis artık null döner (negatif cache); enrich işlemini sessizce atla,
            // alanlar boş kalır. Çağıran zaten try/catch sarmalı içinde.
            return;
        }
        r.setLastPrice(bond.getIndicatorValue());
        r.setChange(bond.getDailyChange());
        r.setChangePercent(bond.getDailyChangePercent());
        r.setRemainingDays(bond.getRemainingDays());
        r.setCouponRate(bond.getCouponRate());

        LocalDate lu = bond.getLastUpdated();
        r.setAsOf(lu != null ? lu.atStartOfDay() : LocalDateTime.now());
    }

    /** Eurobond BI chart 1Y × canlı kur → TL kapanış serisi. */
    private List<BigDecimal> eurobondCloses1y(String isin) {
        try {
            EurobondDetail d = eurobondService.detail(isin);
            BigDecimal rate = d != null && d.getFxRate() != null ? d.getFxRate() : BigDecimal.ONE;
            List<EurobondChartPoint> pts = eurobondService.chart(isin, "1Y");
            if (pts == null || pts.isEmpty()) {
                return null;
            }
            return pts.stream()
                    .map(EurobondChartPoint::close)
                    .filter(Objects::nonNull)
                    .map(c -> c.multiply(rate).setScale(4, RoundingMode.HALF_UP))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return null;
        }
    }

    /** EVDS DİBS 1Y indicator history kapanışları. */
    private List<BigDecimal> evdsCloses1y(String code) {
        try {
            List<EvdsBondHistoryPoint> hist =
                    evdsBondService.getEvdsBondHistory(code.trim(), BondPeriod.ONE_YEAR);
            if (hist == null) {
                return null;
            }
            return hist.stream()
                    .map(EvdsBondHistoryPoint::getIndicatorValue)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return null;
        }
    }
}
