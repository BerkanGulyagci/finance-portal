package com.finance.portal.portfolio.application.viop.valuation;

import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.AssetClass;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.SettlementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ViopValuationService} saf BigDecimal matematiği testleri — mock yok, dependency yok.
 */
class ViopValuationServiceTest {

    private final ViopValuationService service = new ViopValuationService();

    /** Tek hisse benzeri spec: multiplier=100, marginRate=0.146 (örn. AKBNK). */
    private static ViopContractSpec stockSpec() {
        return new ViopContractSpec("AKBNK", AssetClass.SINGLE_STOCK,
                new BigDecimal("100"), new BigDecimal("0.146"), "TRY", SettlementType.PHYSICAL);
    }

    // ---------------------------------------------------------------- directionSign

    @Test
    @DisplayName("directionSign: LONG ve null → +1")
    void directionSign_long() {
        assertThat(service.directionSign("LONG")).isEqualByComparingTo("1");
        assertThat(service.directionSign(null)).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("directionSign: SHORT (büyük/küçük harf, boşluklu) → -1")
    void directionSign_short() {
        assertThat(service.directionSign("SHORT")).isEqualByComparingTo("-1");
        assertThat(service.directionSign("short")).isEqualByComparingTo("-1");
        assertThat(service.directionSign("  Short  ")).isEqualByComparingTo("-1");
    }

    @Test
    @DisplayName("directionSign: tanınmayan değer → +1 (LONG default)")
    void directionSign_unknownDefaultsLong() {
        assertThat(service.directionSign("FOO")).isEqualByComparingTo("1");
        assertThat(service.directionSign("")).isEqualByComparingTo("1");
    }

    // ---------------------------------------------------------------- notional

    @Test
    @DisplayName("notional = qty × price × multiplier")
    void notional_basic() {
        // 2 × 50.25 × 100 = 10050.00
        BigDecimal n = service.notional(new BigDecimal("2"), new BigDecimal("50.25"), stockSpec());
        assertThat(n).isEqualByComparingTo("10050.00");
    }

    @Test
    @DisplayName("notional: herhangi bir argüman null ise ZERO")
    void notional_nullArgs() {
        assertThat(service.notional(null, new BigDecimal("50"), stockSpec())).isEqualByComparingTo("0");
        assertThat(service.notional(new BigDecimal("2"), null, stockSpec())).isEqualByComparingTo("0");
        assertThat(service.notional(new BigDecimal("2"), new BigDecimal("50"), null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("notional: qty=0 → 0")
    void notional_zeroQty() {
        assertThat(service.notional(BigDecimal.ZERO, new BigDecimal("50.25"), stockSpec()))
                .isEqualByComparingTo("0.00");
    }

    // ---------------------------------------------------------------- marginPosted

    @Test
    @DisplayName("marginPosted = qty × entryPrice × multiplier × marginRate")
    void marginPosted_basic() {
        // 3 × 40 × 100 × 0.146 = 1752.00
        BigDecimal m = service.marginPosted(new BigDecimal("3"), new BigDecimal("40"), stockSpec());
        assertThat(m).isEqualByComparingTo("1752.00");
    }

    @Test
    @DisplayName("marginPosted: HALF_UP ile 2 ondalığa yuvarlama")
    void marginPosted_rounding() {
        // 1 × 10.555 × 100 × 0.15 = 158.325 → 158.33 (HALF_UP)
        ViopContractSpec spec = new ViopContractSpec("X", AssetClass.SINGLE_STOCK,
                new BigDecimal("100"), new BigDecimal("0.15"), "TRY", SettlementType.PHYSICAL);
        BigDecimal m = service.marginPosted(new BigDecimal("1"), new BigDecimal("10.555"), spec);
        assertThat(m).isEqualByComparingTo("158.33");
    }

    @Test
    @DisplayName("marginPosted: null argüman → ZERO")
    void marginPosted_nullArgs() {
        assertThat(service.marginPosted(null, new BigDecimal("40"), stockSpec())).isEqualByComparingTo("0");
        assertThat(service.marginPosted(new BigDecimal("3"), null, stockSpec())).isEqualByComparingTo("0");
        assertThat(service.marginPosted(new BigDecimal("3"), new BigDecimal("40"), null)).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- pnl

    @Test
    @DisplayName("pnl LONG: fiyat yükselince kâr (+)")
    void pnl_longProfit() {
        // (55 - 50) × 2 × 100 × (+1) = 1000.00
        BigDecimal p = service.pnl(new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("55"), stockSpec(), "LONG");
        assertThat(p).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("pnl LONG: fiyat düşünce zarar (−)")
    void pnl_longLoss() {
        // (45 - 50) × 2 × 100 × (+1) = -1000.00
        BigDecimal p = service.pnl(new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("45"), stockSpec(), "LONG");
        assertThat(p).isEqualByComparingTo("-1000.00");
    }

    @Test
    @DisplayName("pnl SHORT: fiyat yükselince zarar — LONG'un tersi işaret")
    void pnl_shortLossOnRise() {
        // (55 - 50) × 2 × 100 × (-1) = -1000.00
        BigDecimal p = service.pnl(new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("55"), stockSpec(), "SHORT");
        assertThat(p).isEqualByComparingTo("-1000.00");
    }

    @Test
    @DisplayName("pnl SHORT: fiyat düşünce kâr (+)")
    void pnl_shortProfitOnFall() {
        // (45 - 50) × 2 × 100 × (-1) = +1000.00
        BigDecimal p = service.pnl(new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("45"), stockSpec(), "SHORT");
        assertThat(p).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("pnl: fiyat değişmemiş → 0")
    void pnl_noChange() {
        BigDecimal p = service.pnl(new BigDecimal("2"), new BigDecimal("50"),
                new BigDecimal("50"), stockSpec(), "LONG");
        assertThat(p).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("pnl: null argüman → ZERO")
    void pnl_nullArgs() {
        assertThat(service.pnl(null, new BigDecimal("50"), new BigDecimal("55"), stockSpec(), "LONG"))
                .isEqualByComparingTo("0");
        assertThat(service.pnl(new BigDecimal("2"), new BigDecimal("50"), new BigDecimal("55"), null, "LONG"))
                .isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- dailyPnl

    @Test
    @DisplayName("dailyPnl LONG: uzlaşma artışında kâr")
    void dailyPnl_longProfit() {
        // (52 - 50) × 4 × 100 × (+1) = 800.00
        BigDecimal d = service.dailyPnl(new BigDecimal("4"), new BigDecimal("52"),
                new BigDecimal("50"), stockSpec(), "LONG");
        assertThat(d).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("dailyPnl SHORT: uzlaşma artışında zarar")
    void dailyPnl_shortLoss() {
        // (52 - 50) × 4 × 100 × (-1) = -800.00
        BigDecimal d = service.dailyPnl(new BigDecimal("4"), new BigDecimal("52"),
                new BigDecimal("50"), stockSpec(), "SHORT");
        assertThat(d).isEqualByComparingTo("-800.00");
    }

    @Test
    @DisplayName("dailyPnl: null argüman → ZERO")
    void dailyPnl_nullArgs() {
        assertThat(service.dailyPnl(new BigDecimal("4"), null, new BigDecimal("50"), stockSpec(), "LONG"))
                .isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- leverage

    @Test
    @DisplayName("leverage = notional / margin (RATE_SCALE=4)")
    void leverage_basic() {
        // 10000 / 1460 = 6.8493 (HALF_UP, 4 ondalık)
        BigDecimal l = service.leverage(new BigDecimal("10000"), new BigDecimal("1460"));
        assertThat(l).isEqualByComparingTo("6.8493");
    }

    @Test
    @DisplayName("leverage: margin=0 → ZERO (division-by-zero guard)")
    void leverage_zeroMargin() {
        assertThat(service.leverage(new BigDecimal("10000"), BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("leverage: null argüman → ZERO")
    void leverage_nullArgs() {
        assertThat(service.leverage(null, new BigDecimal("1460"))).isEqualByComparingTo("0");
        assertThat(service.leverage(new BigDecimal("10000"), null)).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- portfolioContribution

    @Test
    @DisplayName("portfolioContribution = marginPosted + pnl (notional değil)")
    void portfolioContribution_basic() {
        BigDecimal c = service.portfolioContribution(new BigDecimal("1752.00"), new BigDecimal("1000.00"));
        assertThat(c).isEqualByComparingTo("2752.00");
    }

    @Test
    @DisplayName("portfolioContribution: negatif pnl margini düşürür")
    void portfolioContribution_negativePnl() {
        BigDecimal c = service.portfolioContribution(new BigDecimal("1752.00"), new BigDecimal("-1000.00"));
        assertThat(c).isEqualByComparingTo("752.00");
    }

    @Test
    @DisplayName("portfolioContribution: null'lar ZERO gibi davranır")
    void portfolioContribution_nullArgs() {
        assertThat(service.portfolioContribution(null, new BigDecimal("1000"))).isEqualByComparingTo("1000.00");
        assertThat(service.portfolioContribution(new BigDecimal("1752"), null)).isEqualByComparingTo("1752.00");
        assertThat(service.portfolioContribution(null, null)).isEqualByComparingTo("0.00");
    }

    // ---------------------------------------------------------------- entegre senaryo

    @Test
    @DisplayName("Entegre: LONG pozisyon — multiplier×marginRate ve notional/leverage tutarlı")
    void integration_longPosition() {
        ViopContractSpec spec = stockSpec(); // mult=100, margin=0.146
        BigDecimal qty = new BigDecimal("5");
        BigDecimal entry = new BigDecimal("20");
        BigDecimal current = new BigDecimal("22");

        BigDecimal margin = service.marginPosted(qty, entry, spec);   // 5×20×100×0.146 = 1460.00
        BigDecimal notional = service.notional(qty, current, spec);   // 5×22×100 = 11000.00
        BigDecimal pnl = service.pnl(qty, entry, current, spec, "LONG"); // (22-20)×5×100 = 1000.00
        BigDecimal contribution = service.portfolioContribution(margin, pnl);
        BigDecimal leverage = service.leverage(notional, margin);     // 11000/1460 = 7.5342

        assertThat(margin).isEqualByComparingTo("1460.00");
        assertThat(notional).isEqualByComparingTo("11000.00");
        assertThat(pnl).isEqualByComparingTo("1000.00");
        assertThat(contribution).isEqualByComparingTo("2460.00");
        assertThat(leverage).isEqualByComparingTo("7.5342");
    }
}
