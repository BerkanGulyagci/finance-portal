package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.service.MarketFxService;
import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import com.finance.portal.market.application.viop.ViopChartPeriod;
import com.finance.portal.market.application.viop.ViopChartService;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import com.finance.portal.portfolio.application.viop.valuation.ViopValuationService;
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
import java.util.Locale;
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
    private final ViopContractSpecRegistry specRegistry;
    private final ViopValuationService valuationService;
    private final MarketFxService marketFxService;

    public FutureHoldingEnricher(ViopService viopService,
                                 ViopChartService viopChartService,
                                 ViopContractSpecRegistry specRegistry,
                                 ViopValuationService valuationService,
                                 MarketFxService marketFxService) {
        this.viopService = viopService;
        this.viopChartService = viopChartService;
        this.specRegistry = specRegistry;
        this.valuationService = valuationService;
        this.marketFxService = marketFxService;
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

        // VİOP spec: çarpan + marjin oranı + para birimi YAML'dan
        ViopContractSpec spec = specRegistry.resolveOrFallback(contractName);
        BigDecimal multiplier = spec.multiplier();
        String direction = holding.getViopDirection(); // null = LONG (geriye uyum)

        // USD-kote kontratta ham fiyat USD'dir; portföy TL-bazlı olduğu için fiyatı USD/TRY ile
        // çarparız. TRY kontratta (66 tane) fxRate=1 → eski davranış AYNEN (etkilenmez).
        // Basitleştirme: hem giriş hem güncel için ANLIK kur (per-date giriş kuru ileride
        // geliştirilebilir — eurobond Model 1 de canlı kur kullanıyor). Kur çekilemezse fxRate=1.
        BigDecimal fxRate = resolveFxRate(spec);

        BigDecimal qty = holding.getTotalQuantity() != null ? holding.getTotalQuantity() : BigDecimal.ZERO;
        BigDecimal totalCost = holding.getTotalCost() != null ? holding.getTotalCost() : BigDecimal.ZERO;
        BigDecimal avgEntry = holding.getAverageCost() != null ? holding.getAverageCost()
                : (qty.signum() > 0 ? totalCost.divide(qty, PRICE_SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        // Notional (risk göstergesi) = qty × (güncel fiyat × fx) × multiplier  → TL
        BigDecimal notional = valuationService.notional(qty, current, spec, fxRate);
        // Margin posted (gerçek bağlı sermaye) = qty × (giriş × fx) × multiplier × marginRate → TL
        BigDecimal marginPosted = valuationService.marginPosted(qty, avgEntry, spec, fxRate);
        // P&L = ((güncel − giriş) × fx) × qty × multiplier × yön  → TL
        BigDecimal pnl = valuationService.pnl(qty, avgEntry, current, spec, direction, fxRate);
        BigDecimal leverage = valuationService.leverage(notional, marginPosted);

        // Market value PORTFÖY TOPLAMINA = margin + cumulative pnl (notional değil!)
        // Notional'i toplama eklemek kaldıraçlı pozisyonda portföyü 8-10x şişirir → yanıltıcı.
        BigDecimal mv = valuationService.portfolioContribution(marginPosted, pnl);

        // USD-kote kontratta tablodaki TÜM para alanları TL'ye çevrilir (portföy TL-bazlı):
        // piyasa değeri/K-Z/maliyet zaten fxRate ile TL; fiyat alanlarını da TL'ye çevirip
        // currency'yi "TRY" yaparız → tabloda tutarlı TL (USD karışmaz). Modal ayrı: orada
        // kontrat fiyatı "$" gösterilir (kullanıcı kotasyon birimini görsün diye).
        boolean isUsdQuote = "USD".equalsIgnoreCase(spec.currency());
        BigDecimal currentTl = isUsdQuote ? current.multiply(fxRate) : current;
        holding.setCurrentPrice(currentTl);
        // Ortalama alış (giriş fiyatı) da USD → TL (tablodaki "Ortalama Alış" sütunu tutarlı).
        // NOT: ideal per-date giriş kuru; şimdilik anlık kur (mv/pnl ile aynı konvansiyon).
        if (isUsdQuote && holding.getAverageCost() != null) {
            holding.setAverageCost(holding.getAverageCost().multiply(fxRate));
        }
        holding.setMarketValue(mv);
        holding.setProfitLoss(pnl);

        // VİOP "Toplam Maliyet" semantiği = YATIRILAN TEMİNAT (kullanıcının cebinden çıkan para),
        // NOT qty × entry (spot mantığı — VİOP'ta tam fiyatı ödemezsin, sadece teminatı bağlarsın).
        // Builder qty × entry'i öncelikli set ediyor; burada override ediyoruz. Bu sayede:
        //   - Portföy "Toplam Maliyet" toplamı = açık pozisyonlar için bağlanan toplam teminat ✓
        //   - "Reel K/Z" hesabı (mv − totalCost) anlamlı kalır (önceden +1350% gibi absürt sonuçlar)
        //   - K/Z % hesabı = pnl / teminat = gerçek getiri oranı
        holding.setTotalCost(marginPosted);

        // USD-kote kontratta tüm para alanları TL'ye çevrildiğinden etiket de TRY olmalı
        // (yoksa tablo "53.541 USD" gibi yanlış gösterir — değer TL ama birim USD çelişkisi).
        holding.setCurrency(isUsdQuote ? "TRY" : (spec.currency() != null ? spec.currency() : "TRY"));
        holding.setName(d.getName() != null ? d.getName() : contractName);
        LocalDateTime asOf = PortfolioDateTimeParse.parseLenient(d.getTime());
        holding.setAsOf(asOf != null ? asOf : LocalDateTime.now());

        // Method-scope direction sign: changePct, change ve realized hesaplarında ortak kullanılır.
        BigDecimal dirSign = valuationService.directionSign(direction);

        // Günlük yüzde — SHORT için yön çevrilir (fiyat düşüşü = SHORT için kar)
        BigDecimal changePct = d.getChangePercent();
        if (changePct != null) {
            holding.setChangePercent(changePct.multiply(dirSign));
        }
        // Gün yüksek/düşük de fiyat → USD kontratta TL'ye çevir (tablo tutarlı TL).
        BigDecimal high = d.getHigh();
        BigDecimal low  = d.getLow();
        holding.setDayHigh(isUsdQuote && high != null ? high.multiply(fxRate) : high);
        holding.setDayLow(isUsdQuote && low != null ? low.multiply(fxRate) : low);

        // VİOP-spesifik alanlar — frontend HoldingsTable + StockDetailPage burada okur
        holding.setViopMultiplier(multiplier);
        holding.setViopMarginRate(spec.marginRate());
        holding.setViopNotional(notional);
        holding.setViopMarginPosted(marginPosted);
        holding.setViopLeverage(leverage);
        if (holding.getViopDirection() == null) {
            holding.setViopDirection("LONG"); // geriye uyumluluk
        }

        // Teminat sağlığı: (margin + pnl) / margin = equity / initialMargin.
        // 1.0 = tam, 0.5 = yarısı yendi, 0 = tükendi, negatif = margin call (alarmı tetikler).
        // mv burada zaten portfolioContribution = marginPosted + pnl (equity) olarak hesaplandı.
        BigDecimal marginRatio = (marginPosted != null && marginPosted.signum() > 0)
                ? mv.divide(marginPosted, 4, RoundingMode.HALF_UP) : null;
        holding.setMarginRatio(marginRatio);
        holding.setMarginStatus(classifyMarginStatus(marginRatio));

        // Not: Realized P/L FUTURE düzeltmesi (multiplier × dirSign) Builder'da yapılır.
        // Enricher PortfolioServiceImpl tarafından parallel cache amacıyla BİR KEZ DAHA
        // çağrılır → idempotent olmayan read+multiply+write burada 1000× bug üretir.

        // Günlük "change" (kontrat başına TL fark) — direction-aware, USD-kote ise fx ile TL'ye:
        //   LONG : (current − prevSettle) × fx × multiplier        — fiyat ↑ = kar
        //   SHORT: (current − prevSettle) × fx × multiplier × −1   — fiyat ↓ = kar (= LONG'un tersi)
        // Frontend bu değeri qty ile çarpıp günlük K/Z'yi hesaplar; direction'ı tekrar uygulamaz.
        // fxRate TRY kontratta 1 → değişmez; USD'de mv/pnl ile tutarlı TL üretir.
        BigDecimal prevSet = d.getPrevSettlementPrice();
        if (prevSet != null) {
            holding.setChange(current.subtract(prevSet).multiply(fxRate).multiply(multiplier).multiply(dirSign)
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

    /**
     * Kontratın para birimine göre fiyat→TL çarpanını çözer.
     * <ul>
     *   <li>TRY (veya null) kontrat → {@code 1} (çevrim yok, davranış değişmez).</li>
     *   <li>USD-kote kontrat (EURUSD, XAUUSD, … — 7 sözleşme) → 1 USD→TL TCMB satış kuru.</li>
     * </ul>
     * Kur çekilemezse (kaynak çöktü / sembol yok) güvenli fallback {@code 1} (ham USD — en azından
     * çökmez; valuation da fxRate≤0'ı 1'e indirger) + warn. Per-date (giriş) kuru ileride
     * geliştirilebilir — şu an hem giriş hem güncel için anlık kur (eurobond Model 1 ile aynı yaklaşım).
     */
    private BigDecimal resolveFxRate(ViopContractSpec spec) {
        if (spec == null || !"USD".equalsIgnoreCase(spec.currency())) {
            return BigDecimal.ONE; // TRY kontrat → çevrim yok
        }
        BigDecimal rate = lookupUsdTryRate();
        if (rate == null || rate.signum() <= 0) {
            log.warn("VIOP USD-kote kontrat için USD/TRY kuru alınamadı (spec code={}); "
                    + "fxRate=1 fallback (fiyat ham USD kalır)", spec.code());
            return BigDecimal.ONE;
        }
        return rate;
    }

    /**
     * 1 USD → TL satış kuru (TCMB). Bulunamazsa null. {@code BondHoldingEnricher.lookupFxRateToTry}
     * ile aynı desen (TCMB güncel kur, satış yoksa alış; unit>1 ise normalize).
     */
    private BigDecimal lookupUsdTryRate() {
        try {
            FxLatestRates latest = marketFxService.getTcmbLatestRates("USD");
            if (latest == null || latest.getRates() == null) {
                return null;
            }
            FxRateItem rate = latest.getRates().stream()
                    .filter(r -> "USD".equalsIgnoreCase(r.getSymbol()))
                    .findFirst().orElse(null);
            if (rate == null) {
                return null;
            }
            BigDecimal sell = rate.getSell() != null ? rate.getSell() : rate.getBuy();
            if (sell == null) {
                return null;
            }
            int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
            return unit > 1
                    ? sell.divide(BigDecimal.valueOf(unit), 8, RoundingMode.HALF_UP)
                    : sell;
        } catch (Exception e) {
            log.debug("VIOP USD/TRY kuru alınamadı: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Teminat oranını üç bantta sınıflar:
     * <ul>
     *   <li>{@code HEALTHY} — oran &gt; 0.50 (teminatın yarısından fazlası sağlam)</li>
     *   <li>{@code WARNING} — 0.25 &lt; oran ≤ 0.50 (yarısı yendi, dikkat)</li>
     *   <li>{@code CRITICAL} — oran ≤ 0.25 (negatif/margin call dahil)</li>
     * </ul>
     */
    private static String classifyMarginStatus(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        if (ratio.compareTo(new BigDecimal("0.50")) > 0) {
            return "HEALTHY";
        }
        if (ratio.compareTo(new BigDecimal("0.25")) > 0) {
            return "WARNING";
        }
        return "CRITICAL";
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
