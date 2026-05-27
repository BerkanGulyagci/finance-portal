package com.finance.portal.portfolio.service.watchlistenrich;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.portfolio.presentation.dto.WatchlistItemResponse;
import com.finance.portal.portfolio.service.support.RasyonetFundLookup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FUND watchlist item zenginleştirmesi. Rasyonet liste + zengin detay (NAV/getiri/PriceHistory)
 * üç-aşamalı fallback: kullanıcı kayıt source-code → TMF/TPF/TAF varsayılan → listed fiyat.
 * Trend metrikleri fon dünyasında "getiri yüzdeleri" (1A/3A/YTD/1Y) olarak temsil edilir;
 * 52w/MA mantığı diğer asset'lerden farklı (FUND applyTrendSignals'ta atlanır).
 *
 * <p>Daha önce {@code PortfolioWatchlistMarketEnricher.enrichFund + applyRasyonetFundDetail +
 * applyRasyonetFundListRow + mapFundMetadata* + applyFundOhlcFromRasyonetHistory +
 * fillFundChangeFromDailyPercent} idi.
 */
@Component
public class FundWatchlistEnricher {

    private final RasyonetFundService rasyonetFundService;

    public FundWatchlistEnricher(RasyonetFundService rasyonetFundService) {
        this.rasyonetFundService = rasyonetFundService;
    }

    public void enrich(WatchlistItemResponse r, String symbol) {
        String code = symbol.trim().toUpperCase(Locale.ROOT);

        RasyonetFundDto listed = RasyonetFundLookup.findByCode(rasyonetFundService, code);
        List<String> detailSources = new ArrayList<>();
        if (listed != null && listed.getSourceCode() != null && !listed.getSourceCode().isBlank()) {
            detailSources.add(listed.getSourceCode().trim().toUpperCase(Locale.ROOT));
        }
        for (String sc : List.of("TMF", "TPF", "TAF")) {
            if (!detailSources.contains(sc)) {
                detailSources.add(sc);
            }
        }

        for (String sc : detailSources) {
            RasyonetFundDetailDto d = rasyonetFundService.getFundDetailRich(code, sc);
            if (d != null && d.getPrice() != null && d.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                applyDetail(r, d);
                return;
            }
        }

        if (listed != null && listed.getPrice() != null
                && listed.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            applyListRow(r, listed);
            return;
        }

        throw new IllegalArgumentException("Fund price not found for code: " + code);
    }

    private void applyListRow(WatchlistItemResponse r, RasyonetFundDto f) {
        r.setLastPrice(f.getPrice());
        r.setCurrency("TRY");
        fillChangeFromDailyPercent(r, f.getPrice(), f.getReturnOneDay());
        mapMetadataFromListDto(r, f);
        r.setAsOf(LocalDateTime.now());
    }

    private void applyDetail(WatchlistItemResponse r, RasyonetFundDetailDto d) {
        BigDecimal nav = d.getPrice();
        r.setLastPrice(nav);
        String cur = d.getCurrencyCode();
        r.setCurrency(cur != null && !cur.isBlank() ? cur : "TRY");
        mapMetadataFromDetailDto(r, d);

        List<RasyonetFundDetailDto.PricePoint> ph = d.getPriceHistory();
        if (!applyOhlcFromHistory(r, ph, nav)) {
            fillChangeFromDailyPercent(r, nav, d.getReturnOneDay());
        }
        if (r.getOpen() == null && r.getLastPrice() != null && r.getChange() != null) {
            r.setOpen(r.getLastPrice().subtract(r.getChange()).setScale(4, RoundingMode.HALF_UP));
        }
        r.setAsOf(LocalDateTime.now());
    }

    private static void mapMetadataFromListDto(WatchlistItemResponse r, RasyonetFundDto f) {
        if (f == null) {
            return;
        }
        if (f.getName() != null && !f.getName().isBlank()) {
            r.setFundName(f.getName());
        }
        if (f.getFundType() != null && !f.getFundType().isBlank()) {
            r.setFundType(f.getFundType());
        }
        r.setFundReturnOneMonth(f.getReturnOneMonth());
        r.setFundReturnThreeMonths(f.getReturnThreeMonths());
        r.setFundReturnYtd(f.getReturnYearToDate());
        r.setFundReturnOneYear(f.getReturnOneYear());
        r.setFundRiskLevel(f.getRiskLevel());
    }

    private static void mapMetadataFromDetailDto(WatchlistItemResponse r, RasyonetFundDetailDto d) {
        if (d == null) {
            return;
        }
        if (d.getName() != null && !d.getName().isBlank()) {
            r.setFundName(d.getName());
        }
        if (d.getFundType() != null && !d.getFundType().isBlank()) {
            r.setFundType(d.getFundType());
        }
        r.setFundReturnOneMonth(d.getReturnOneMonth());
        r.setFundReturnThreeMonths(d.getReturnThreeMonths());
        r.setFundReturnYtd(d.getReturnYearToDate());
        r.setFundReturnOneYear(d.getReturnOneYear());
        r.setFundRiskLevel(d.getRiskLevel());
    }

    /** Rasyonet PriceHistory'den (varsa) son 5 günlük window + prev close → open/high/low/change. */
    private static boolean applyOhlcFromHistory(WatchlistItemResponse r,
            List<RasyonetFundDetailDto.PricePoint> ph, BigDecimal nav) {
        if (ph == null || ph.isEmpty() || nav == null) {
            return false;
        }
        int n = ph.size();
        int windowStart = Math.max(0, n - 5);
        BigDecimal maxP = null;
        BigDecimal minP = null;
        for (int i = windowStart; i < n; i++) {
            BigDecimal p = ph.get(i).getPrice();
            if (p == null) {
                continue;
            }
            maxP = maxP == null ? p : maxP.max(p);
            minP = minP == null ? p : minP.min(p);
        }
        maxP = maxP == null ? nav : maxP.max(nav);
        minP = minP == null ? nav : minP.min(nav);
        r.setHigh(maxP.setScale(4, RoundingMode.HALF_UP));
        r.setLow(minP.setScale(4, RoundingMode.HALF_UP));

        if (n >= 2) {
            BigDecimal prevClose = ph.get(n - 2).getPrice();
            if (prevClose != null) {
                r.setOpen(prevClose.setScale(4, RoundingMode.HALF_UP));
                BigDecimal ch = nav.subtract(prevClose);
                r.setChange(ch.setScale(4, RoundingMode.HALF_UP));
                if (prevClose.compareTo(BigDecimal.ZERO) != 0) {
                    r.setChangePercent(ch.divide(prevClose, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP));
                }
                return true;
            }
        }
        return false;
    }

    /** Detay PriceHistory yoksa: returnOneDay% × fiyat = günlük TL değişimi (open implied). */
    private static void fillChangeFromDailyPercent(WatchlistItemResponse r, BigDecimal price,
                                                   BigDecimal returnOneDayPct) {
        if (price == null || returnOneDayPct == null) {
            return;
        }
        r.setChangePercent(returnOneDayPct.setScale(2, RoundingMode.HALF_UP));
        BigDecimal denom = BigDecimal.ONE.add(
                returnOneDayPct.divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP));
        if (denom.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal impliedPrev = price.divide(denom, 8, RoundingMode.HALF_UP);
        r.setChange(price.subtract(impliedPrev).setScale(4, RoundingMode.HALF_UP));
        if (r.getLastPrice() != null && r.getChange() != null) {
            r.setOpen(r.getLastPrice().subtract(r.getChange()).setScale(4, RoundingMode.HALF_UP));
        }
    }
}
