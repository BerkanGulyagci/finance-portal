package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.portfolio.presentation.dto.PortfolioHoldingResponse;
import com.finance.portal.portfolio.service.support.PortfolioMovingAverage;
import com.finance.portal.portfolio.service.support.RasyonetFundLookup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.FUND_MONEY_SCALE;

/**
 * FUND holding zenginleştirmesi. Kaynak: {@link RasyonetFundService} (Rasyonet kart/liste API'leri).
 *
 * <p>Üç fallback adımı: önce kullanıcının kayıtlı fon kodu için liste endpoint'inden
 * {@code sourceCode}'u kullanan zengin detay (NAV + getiri + 1Y NAV geçmişi), sonra
 * varsayılan kaynak kodları (TMF/TPF/TAF), son çare olarak yalnız liste verisi (geçmiş yok).
 * Davranış {@code PortfolioHoldingMarketEnricher.enrichFundHolding(...)} eski kodundan aynen taşındı.
 */
@Component
public class FundHoldingEnricher {

    private final RasyonetFundService rasyonetFundService;

    public FundHoldingEnricher(RasyonetFundService rasyonetFundService) {
        this.rasyonetFundService = rasyonetFundService;
    }

    public void enrich(PortfolioHoldingResponse holding) {
        String code = holding.getSymbol().trim().toUpperCase(Locale.ROOT);

        RasyonetFundDto listed = RasyonetFundLookup.findByCode(rasyonetFundService, code);
        List<String> sources = new ArrayList<>();
        if (listed != null && listed.getSourceCode() != null && !listed.getSourceCode().isBlank()) {
            sources.add(listed.getSourceCode().trim().toUpperCase(Locale.ROOT));
        }
        for (String sc : List.of("TMF", "TPF", "TAF")) {
            if (!sources.contains(sc)) {
                sources.add(sc);
            }
        }

        for (String sc : sources) {
            RasyonetFundDetailDto d = rasyonetFundService.getFundDetailRich(code, sc);
            if (d != null && d.getPrice() != null && d.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                applyDetail(holding, d);
                return;
            }
        }

        if (listed != null && listed.getPrice() != null
                && listed.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            applyDetailFromListDto(holding, listed);
            return;
        }

        throw new IllegalArgumentException("Fund price not found for code: " + code);
    }

    /**
     * Rasyonet zengin kart → holding alanları. NAV, getiriler, günlük % → {@code changePercent},
     * pay başı günlük TL farkı → {@code change} (HoldingsTable günlük TL = qty × change),
     * son ~1 yıl NAV geçmişinden 52 hafta aralığı + MA20/MA50 (trend yedek).
     */
    private void applyDetail(PortfolioHoldingResponse holding, RasyonetFundDetailDto d) {
        BigDecimal price = d.getPrice();
        BigDecimal mv = price.multiply(holding.getTotalQuantity()).setScale(FUND_MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal pl = mv.subtract(holding.getTotalCost()).setScale(FUND_MONEY_SCALE, RoundingMode.HALF_UP);

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        String cur = d.getCurrencyCode();
        String safeCurrency = isValidIsoCurrency(cur) ? cur : "TRY";
        holding.setCurrency(safeCurrency);
        holding.setAsOf(LocalDateTime.now());
        if (d.getName() != null && !d.getName().isBlank()) {
            holding.setName(d.getName());
        }
        holding.setReturnOneDay(d.getReturnOneDay());
        holding.setReturnOneMonth(d.getReturnOneMonth());
        holding.setReturnThreeMonths(d.getReturnThreeMonths());

        mapDailyChange(holding, price, d.getReturnOneDay());
        applyNavHistoryStats(holding, d.getPriceHistory(), price);
    }

    /** Liste endpoint'inden gelen fiyat; fiyat geçmişi yok → günlük %/TL yine map edilir, 52w/MA boş kalabilir. */
    private void applyDetailFromListDto(PortfolioHoldingResponse holding, RasyonetFundDto listed) {
        RasyonetFundDetailDto d = new RasyonetFundDetailDto();
        d.setPrice(listed.getPrice());
        d.setCurrencyCode("TRY");
        d.setName(listed.getName());
        d.setReturnOneDay(listed.getReturnOneDay());
        d.setReturnOneMonth(listed.getReturnOneMonth());
        d.setReturnThreeMonths(listed.getReturnThreeMonths());
        d.setPriceHistory(null);
        applyDetail(holding, d);
    }

    /** returnOneDay (%) → changePercent; pay başı günlük TL → change. */
    private static void mapDailyChange(PortfolioHoldingResponse holding,
                                       BigDecimal price,
                                       BigDecimal returnOneDayPct) {
        if (returnOneDayPct == null) {
            holding.setChange(null);
            holding.setChangePercent(null);
            return;
        }
        holding.setChangePercent(returnOneDayPct);
        if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal delta = price.multiply(returnOneDayPct)
                    .divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP);
            holding.setChange(delta);
        } else {
            holding.setChange(null);
        }
    }

    /**
     * Rasyonet {@code LastYearReturnPrice} serisinden min/max NAV ve kapanış serisi MA20/MA50.
     */
    private static void applyNavHistoryStats(PortfolioHoldingResponse holding,
                                             List<RasyonetFundDetailDto.PricePoint> rawPh,
                                             BigDecimal currentNav) {
        if (rawPh == null || rawPh.isEmpty()) {
            return;
        }
        List<RasyonetFundDetailDto.PricePoint> ph = new ArrayList<>(rawPh);
        ph.sort(Comparator.comparing(RasyonetFundDetailDto.PricePoint::getDate,
                Comparator.nullsLast(String::compareTo)));

        List<BigDecimal> closes = ph.stream()
                .map(RasyonetFundDetailDto.PricePoint::getPrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (closes.isEmpty()) {
            return;
        }

        BigDecimal hi = closes.stream().max(BigDecimal::compareTo).orElse(null);
        BigDecimal lo = closes.stream().min(BigDecimal::compareTo).orElse(null);
        if (currentNav != null) {
            hi = hi == null ? currentNav : hi.max(currentNav);
            lo = lo == null ? currentNav : lo.min(currentNav);
        }
        holding.setFiftyTwoWeekHigh(hi);
        holding.setFiftyTwoWeekLow(lo);

        List<BigDecimal> forMa = new ArrayList<>(closes);
        if (currentNav != null
                && (forMa.isEmpty() || forMa.get(forMa.size() - 1).compareTo(currentNav) != 0)) {
            forMa.add(currentNav);
        }
        holding.setMa20(PortfolioMovingAverage.simpleMa(forMa, 20));
        holding.setMa50(PortfolioMovingAverage.simpleMa(forMa, 50));
    }

    /**
     * Verilen string'in bilinen ISO 4217 para birimi kodu olup olmadığını kontrol eder.
     * Rasyonet'in kaynak kodları (TMF, TPF, TAF) gibi değerleri filtreler.
     */
    private static boolean isValidIsoCurrency(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        try {
            Currency.getInstance(code.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
