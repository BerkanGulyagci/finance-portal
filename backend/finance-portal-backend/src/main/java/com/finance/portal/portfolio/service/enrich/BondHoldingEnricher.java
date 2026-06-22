package com.finance.portal.portfolio.service.enrich;

import com.finance.portal.market.application.bond.evds.BondPeriod;
import com.finance.portal.market.application.bond.evds.EvdsBondHistoryPoint;
import com.finance.portal.market.application.bond.evds.EvdsBondInstrument;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.bond.evds.model.BondCategory;
import com.finance.portal.market.application.bond.evds.model.BondClassifier;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.application.gold.GoldMarketService;
import com.finance.portal.market.application.gold.GoldSpotResponse;
import com.finance.portal.market.application.service.MarketFxService;
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
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.profitLoss;
import static com.finance.portal.portfolio.service.enrich.PortfolioEnrichmentMath.profitLossPercent;

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

    /**
     * TCMB konvansiyonu: DİBS "Bugünkü Değer" 100 TL nominal üzerinden kote edilir
     * ({@code dibs1.txt} resmi başlığı). Piyasa değeri ve maliyet bu çarpan ile ölçeklenir:
     * <pre>
     *   marketValue = qty_nominal × price / 100
     * </pre>
     * Bu ölçek hem EVDS DİBS hem Eurobond (BI quote'u da 100 nominal üzerinden) için geçerli.
     */
    private static final BigDecimal PAR_SCALE = new BigDecimal("100");

    private final EvdsBondService evdsBondService;
    private final EurobondService eurobondService;
    private final MarketFxService marketFxService;
    private final GoldMarketService goldMarketService;

    /** {@code qty × price / 100} — DİBS/Eurobond piyasa değeri formülü, MONEY_SCALE'e yuvarlanmış. */
    private static BigDecimal bondMarketValue(BigDecimal price, BigDecimal qty) {
        if (price == null || qty == null) return null;
        return price.multiply(qty)
                .divide(PAR_SCALE, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BondHoldingEnricher(EvdsBondService evdsBondService,
                               EurobondService eurobondService,
                               MarketFxService marketFxService,
                               GoldMarketService goldMarketService) {
        this.evdsBondService = evdsBondService;
        this.eurobondService = eurobondService;
        this.marketFxService = marketFxService;
        this.goldMarketService = goldMarketService;
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

        // Önce 1Y history çek — vadesi dolan bonolarda indicator boş geldiğinde son
        // kapanışı fallback olarak kullanırız. Aynı sorgu 52w/MA için de gereklidir.
        List<EvdsBondHistoryPoint> hist = null;
        try {
            hist = evdsBondService.getEvdsBondHistory(code, BondPeriod.ONE_YEAR);
        } catch (Exception e) {
            log.debug("Bond history fetch failed for {}: {}", code, e.getMessage());
        }

        EvdsBondInstrument bond = null;
        BigDecimal price = null;
        BigDecimal change = null;
        BigDecimal changePercent = null;
        String type = null;
        LocalDate lu = null;
        try {
            bond = evdsBondService.getEvdsBondDetail(code);
            if (bond != null) {
                price = bond.getIndicatorValue();
                change = bond.getDailyChange();
                changePercent = bond.getDailyChangePercent();
                type = bond.getType();
                lu = bond.getLastUpdated();
            }
        } catch (Exception e) {
            log.debug("EVDS bond detail unavailable for {}: {}", code, e.getMessage());
        }

        // Fallback: indicator yoksa (vadesi dolmuş / EVDS'de yok) history serisinin son kapanışı.
        boolean usedHistoryFallback = false;
        LocalDate fallbackDate = null;
        if ((price == null || price.compareTo(BigDecimal.ZERO) <= 0) && hist != null && !hist.isEmpty()) {
            for (int i = hist.size() - 1; i >= 0; i--) {
                EvdsBondHistoryPoint pt = hist.get(i);
                if (pt.getIndicatorValue() != null && pt.getIndicatorValue().compareTo(BigDecimal.ZERO) > 0) {
                    price = pt.getIndicatorValue();
                    fallbackDate = pt.getDate();
                    usedHistoryFallback = true;
                    break;
                }
            }
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            // Hem indicator hem history yok → enricher null bırakır, UI "—" gösterir.
            holding.setCurrency("TRY");
            if (type != null && !type.isBlank()) {
                holding.setName(code + " · " + type);
            }
            return;
        }

        // TCMB DİBS Bugünkü Değer 100 nominal üzerinden kote: mv = qty × price / 100
        BigDecimal mv = bondMarketValue(price, holding.getTotalQuantity());

        BondCategory category = bond != null ? bond.getCategory() : null;

        // EVDS "Gösterge Değeri" 100 TL nominal üzerinden kote edilir. KİRLİ/TEMİZ AYRIMI:
        // TCMB dibs1.txt'te KUPONLU kıymetler (Kuponlu DT, TLREF, TÜFE-endeksli, kira sertifikaları)
        // "Present Value INCLUDING Coupon" başlığı altındadır → değer KİRLİ FİYAT'tır (birikmiş
        // faiz/kupon dahil). KUPONSUZ kıymetler (bono, stripler) "Present Value" → temiz=kirli.
        // (Kanıt: TRT080726T21 vadeye 16 gün kala 108.31 — temiz olsa par~100 olurdu; fark birikmiş
        // faiz. API değeri = dibs1.txt "kupon dahil" değeriyle birebir.) Kullanıcı zaten bu kirli
        // değerle alıp sattığı için K/Z tutarlıdır; eurobond'dan farklı olarak burada kirli fiyatı
        // AYRICA hesaplamaya gerek yoktur (EVDS hazır verir).
        //
        // TÜFE-endeksli bondlarda günlük kotasyona TÜFE çarpanı GÖMÜLÜ DEĞİLDİR; endeksleme yalnız
        // kupon/vade ödemesine yansır (bkz. BondMaturityScheduler — TÜFE bondları otomatik itfadan
        // elle dışlanmıştır). Bu yüzden TÜFE bondları Tier-1 DİBS ile aynı /100 ölçeğini kullanır;
        // ek TÜFE çarpanı UYGULANMAZ. Reel getiri PortfolioRealReturnEnricher'da ayrı hesaplanır.

        // Yabancı para cinsli DT (Section 5/6): EVDS "Değer" zaten TL bazlı kotasyon —
        // dış FX çevirisi YAPILMAZ; currency etiketi de TRY kalır (değerler TL).
        // (Kategori rozeti UI'da "EUR/USD" gösterir ama TL hesaba göre.)

        // Per-unit nominal quote (yalnız altına dayalı senet, bkz. BondCategory#usesPerUnitNominalQuote):
        //   - Altına dayalı senet (Section 4): EVDS "Değer/1 adet" — birim 1 gram has altın, TL/adet.
        // Bu ailede qty × price doğrudan TL piyasa değerini verir, /100 YOK; cost tarafıyla simetri:
        // HoldingsBuilder aynı kategoride effectivePrice'ı /100 BÖLMEZ → cost da nominal birim üzerinden,
        // ratio (PL%) doğru kalır. Diğer tüm DİBS/Eurobond/TÜFE/Kira sertifikaları klasik %-of-par
        // konvansiyonunu kullanır (qty × price / 100).
        if (mv != null && category != null && category.usesPerUnitNominalQuote()) {
            BigDecimal qty = holding.getTotalQuantity() != null
                    ? holding.getTotalQuantity() : BigDecimal.ZERO;
            mv = qty.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal pl = profitLoss(mv, holding.getTotalCost());
        BigDecimal plPct = profitLossPercent(mv, holding.getTotalCost());

        holding.setCurrentPrice(price);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setProfitLossPercent(plPct);
        // FX-cinsli bondlarda yukarıda zaten setCurrency(fxCurrency) yapıldı; aksi takdirde TRY.
        if (holding.getCurrency() == null || holding.getCurrency().isBlank()) {
            holding.setCurrency("TRY");
        }
        holding.setChange(change);
        holding.setChangePercent(changePercent);
        if (category != null) {
            holding.setCategory(category.name());
        }
        // Kupon oranı (.ORAN serisi) holdings'e taşınır. paysCoupon flag'i: yalnız PERİYODİK kupon
        // ödeyen kategorilerde true. Frontend "Kupon Ekle" butonunu buna göre gösterir.
        if (bond != null) {
            holding.setCouponRate(bond.getCouponRate());
        }
        if (category != null) {
            // TLREF/Kira/TÜFE-Kira ailesinde strip ayrı kategori değildir; ISIN sonekinden ayrılır
            // (dibs1.txt'te aynı bölümde T=tam, A=ana para stripi, K=kupon stripi listelenir — kanıt:
            // TÜFE-kira TRD..K değeri ~11 = tek kupon, T değeri ~770 = tam). K = kupon stripi
            // (tek-seferlik, periyodik kupon ödemez → buton ÇIKMAZ). Aksi halde kategori paysCoupon().
            boolean stripFamily = category == BondCategory.TLREF_INDEXED_BOND
                    || category == BondCategory.LEASE_CERTIFICATE
                    || category == BondCategory.INFLATION_INDEXED_LEASE_CERTIFICATE;
            boolean kStrip = stripFamily
                    && BondClassifier.lastIsinLetterBeforeDigits(code) == 'K';
            holding.setPaysCoupon(category.paysCoupon() && !kStrip);
        }
        holding.setAsOf(lu != null ? lu.atStartOfDay()
                : fallbackDate != null ? fallbackDate.atStartOfDay()
                : LocalDateTime.now());

        if (type != null && !type.isBlank()) {
            String suffix = usedHistoryFallback ? " · " + type + " (vadesi geçti)" : " · " + type;
            holding.setName(code + suffix);
        } else if (usedHistoryFallback) {
            holding.setName(code + " (vadesi geçti)");
        }

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
        // KİRLİ fiyat değerleme: işlem modalı eurobondu KİRLİ fiyatla (temiz + birikmiş faiz) kaydeder
        // (bkz. EurobondController#priceAt). Maliyet kirli olduğu için piyasa değeri de kirli fiyatla
        // hesaplanır → K/Z tutarlı (kirli mv − kirli maliyet). dirtyPriceTry yoksa (kupon künyesi eksik
        // ya da hesap yapılamadı) güvenli şekilde temiz lastPriceTry'a düşülür (geriye uyumlu).
        BigDecimal priceTry = d.getDirtyPriceTry() != null ? d.getDirtyPriceTry() : d.getLastPriceTry();
        BigDecimal qty = holding.getTotalQuantity() != null ? holding.getTotalQuantity() : BigDecimal.ZERO;
        // BI eurobond kote'si % nominal — DİBS ile aynı 100-üzeri ölçek: mv = qty × priceTry / 100
        BigDecimal mv = bondMarketValue(priceTry, qty);

        // Maliyet builder tarafından ZATEN TL hesaplanır: eurobond fiyatı modal/autofill'de KİRLİ TL
        // girilir (kirli kote × o günün TCMB satış kuru — tarihsel FX fiyata gömülü). Bu yüzden burada
        // ek FX çevirimi YAPILMAZ; aksi halde kur çifte sayılıp cost ~FX katı şişerdi. currency = TRY.
        BigDecimal cost = holding.getTotalCost() != null ? holding.getTotalCost() : BigDecimal.ZERO;
        BigDecimal pl = mv.subtract(cost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal plPct = profitLossPercent(mv, holding.getTotalCost());

        holding.setCurrentPrice(priceTry);
        holding.setMarketValue(mv);
        holding.setProfitLoss(pl);
        holding.setProfitLossPercent(plPct);
        holding.setCurrency("TRY");
        holding.setName(d.getName() != null ? d.getName() : isin);
        holding.setChangePercent(d.getChangePercent());
        // Eurobond kupon oranı BI'da string ("11.875%") → parse edip holdings'e taşı (kupon modalı için).
        // Eurobond'lar periyodik kupon öder → couponRate > 0 ise paysCoupon = true ("Kupon Ekle" çıkar).
        BigDecimal euroCoupon = com.finance.portal.market.application.bond.eurobond.model
                .AccruedInterestCalculator.parsePercent(d.getCouponRate());
        if (euroCoupon != null) {
            holding.setCouponRate(euroCoupon);
            holding.setPaysCoupon(euroCoupon.signum() > 0);
        }
        // Günlük mutlak değişim (100 nominal başına TL) — BI yalnız yüzde verir, fiyattan türet:
        // change = priceTry − dünkü = priceTry × pct / (100 + pct). Portföy özeti "Günlük K/Z"
        // qty × change kullandığı için (changePercent değil) bu olmadan eurobond özete yansımıyordu.
        BigDecimal chgPct = d.getChangePercent();
        if (chgPct != null && chgPct.signum() != 0) {
            BigDecimal denom = BigDecimal.valueOf(100).add(chgPct);
            if (denom.signum() != 0) {
                holding.setChange(priceTry.multiply(chgPct)
                        .divide(denom, MONEY_SCALE, RoundingMode.HALF_UP));
            }
        }
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

    // ── Tier 3 — yabancı para / altın yardımcıları ───────────────────────────

    /**
     * Yabancı para cinsli DİBS için para birimini tahmin et. TCMB Section 5 EUR, Section 6 USD;
     * ISIN/CBRT kodundan kesin ayrım yapılamadığında varsayılan USD.
     * <p>Gelecek iyileştirme: EVDS SERIE_NAME/CBRT kodu içeriğinden EUR/USD parsing.
     */
    private static String guessFxBondCurrency(String code, EvdsBondInstrument bond) {
        // Bond name veya CBRT kodunda "EUR" ipucu varsa EUR; aksi takdirde USD.
        if (bond != null) {
            String[] hints = { bond.getType(), bond.getCbrtCode() };
            for (String hint : hints) {
                if (hint != null && hint.toUpperCase(Locale.ROOT).contains("EUR")) {
                    return "EUR";
                }
            }
        }
        return "USD";
    }

    /** 1 birim {@code currency} → TL satış kuru (TCMB). Bulunamazsa null. */
    private BigDecimal lookupFxRateToTry(String currency) {
        if (currency == null || currency.isBlank()) return null;
        try {
            String cur = currency.trim().toUpperCase(Locale.ROOT);
            FxLatestRates latest = marketFxService.getTcmbLatestRates(cur);
            if (latest == null || latest.getRates() == null) return null;
            FxRateItem rate = latest.getRates().stream()
                    .filter(r -> cur.equalsIgnoreCase(r.getSymbol()))
                    .findFirst().orElse(null);
            if (rate == null) return null;
            BigDecimal sell = rate.getSell() != null ? rate.getSell() : rate.getBuy();
            if (sell == null) return null;
            int unit = rate.getUnit() > 1 ? rate.getUnit() : 1;
            return unit > 1
                    ? sell.divide(BigDecimal.valueOf(unit), 8, RoundingMode.HALF_UP)
                    : sell;
        } catch (Exception e) {
            log.debug("FX-cinsli bond için kur alınamadı ({}): {}", currency, e.getMessage());
            return null;
        }
    }

    /** Canlı has altın gram TL fiyatı (BIST üzerinden). Bulunamazsa null. */
    private BigDecimal lookupGoldGramTry() {
        try {
            GoldSpotResponse spot = goldMarketService.getSpotGold();
            if (spot != null) {
                if (spot.getOfficialPureGoldGramTry() != null) {
                    return spot.getOfficialPureGoldGramTry();
                }
                if (spot.getGramCloseTry() != null) {
                    return spot.getGramCloseTry();
                }
            }
        } catch (Exception e) {
            log.debug("Altın gram TL alınamadı: {}", e.getMessage());
        }
        return null;
    }
}
