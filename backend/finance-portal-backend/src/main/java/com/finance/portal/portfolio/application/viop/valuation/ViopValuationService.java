package com.finance.portal.portfolio.application.viop.valuation;

import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * VİOP pozisyonu için saf matematik: notional, teminat, kaldıraç, kâr/zarar.
 * Stateless, hiçbir external dependency yok — birim test trivial.
 *
 * <p><b>Formüller (yön LONG=+1, SHORT=−1):</b>
 * <pre>
 *   notional       = qty × price × multiplier
 *   marginPosted   = qty × avgEntryPrice × multiplier × marginRate
 *   pnl            = qty × (currentPrice − avgEntryPrice) × multiplier × directionSign
 *   leverage       = notional / marginPosted
 *   dailyPnl       = qty × (settlement − prevSettlement) × multiplier × directionSign
 * </pre>
 *
 * <p>Portföy toplamına {@code marginPosted + pnl} katılır (notional değil — kaldıraçlı
 * pozisyon küçük portföyü 8-10x şişirir, yanlış yönlendirici).
 *
 * <p><b>Para birimi (FX) çevirimi:</b> Portföy TL-bazlıdır. USD-kote kontratlarda
 * (örn. EURUSD, XAUUSD — {@code spec.currency() == "USD"}) ham fiyat USD'dir; her formül
 * fiyatı önce TL'ye çevirir: {@code price_TL = price_USD × fxRate}. {@code fxRate} = 1 USD → TL
 * (TCMB satış kuru), çağıran enricher tarafından geçilir. TRY kontratlarda {@code fxRate = 1}
 * (değişmez davranış). {@code fxRate} parametresiz eski imzalar {@code fxRate = 1} ile yeni
 * imzalara delege eder — geriye tam uyumlu (TRY kontratlar + mevcut çağrılar aynen çalışır).
 * Not: {@code marginAmount} (scrape) zaten TL tutar → FX UYGULANMAZ.
 */
@Service
public class ViopValuationService {

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;

    /** Yön çarpanı: LONG=+1, SHORT=−1. */
    public BigDecimal directionSign(String direction) {
        if (direction == null) return BigDecimal.ONE; // backward-compat: LONG default
        return "SHORT".equalsIgnoreCase(direction.trim()) ? BigDecimal.ONE.negate() : BigDecimal.ONE;
    }

    /**
     * Kontratın para birimine göre fiyatı TL'ye çeviren çarpan.
     * USD-kote spec'te {@code fxRate} (1 USD→TL), aksi halde 1 (TRY: değişmez).
     * {@code fxRate} null/≤0 ise güvenli fallback 1 (ham fiyat — çökmez; enricher zaten warn'lar).
     */
    private static BigDecimal effectiveFx(ViopContractSpec spec, BigDecimal fxRate) {
        if (spec == null || !"USD".equalsIgnoreCase(spec.currency())) {
            return BigDecimal.ONE; // TRY (veya bilinmeyen) → çevrim yok
        }
        return (fxRate != null && fxRate.signum() > 0) ? fxRate : BigDecimal.ONE;
    }

    /**
     * Nominal pozisyon büyüklüğü (piyasada kontrol edilen tutar).
     * Risk göstergesi — portföy toplamına eklenmez.
     */
    public BigDecimal notional(BigDecimal qty, BigDecimal currentPrice, ViopContractSpec spec) {
        return notional(qty, currentPrice, spec, BigDecimal.ONE);
    }

    /**
     * Nominal pozisyon büyüklüğü — USD-kote kontratta fiyat {@code fxRate} ile TL'ye çevrilir.
     * {@code notional = qty × (price × fx) × multiplier}. TRY kontratta fx etkisizdir.
     */
    public BigDecimal notional(BigDecimal qty, BigDecimal currentPrice, ViopContractSpec spec, BigDecimal fxRate) {
        if (anyNull(qty, currentPrice, spec)) return BigDecimal.ZERO;
        BigDecimal priceTl = currentPrice.multiply(effectiveFx(spec, fxRate));
        return qty.multiply(priceTl).multiply(spec.multiplier())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Başlangıç teminatı — pozisyon açarken hesaba bloke edilen TL (veya kontrat parası).
     * Cüzdandan gerçekten çıkan tutar.
     *
     * <p>İki yol:
     * <ul>
     *   <li>{@code spec.marginAmount} VAR (İş Yatırım scrape) → {@code qty × marginAmount}.
     *       Takasbank'ın verdiği KESİN tutar; fiyattan bağımsız, en doğru.</li>
     *   <li>{@code marginAmount} null (YAML statik spec) → eski oran yolu:
     *       {@code qty × avgEntryPrice × multiplier × marginRate}.</li>
     * </ul>
     */
    public BigDecimal marginPosted(BigDecimal qty, BigDecimal avgEntryPrice, ViopContractSpec spec) {
        return marginPosted(qty, avgEntryPrice, spec, BigDecimal.ONE);
    }

    /**
     * Başlangıç teminatı — USD-kote kontratta oran-bazlı yolda giriş fiyatı {@code fxRate} ile
     * TL'ye çevrilir: {@code qty × (avgEntry × fx) × multiplier × marginRate}.
     *
     * <p><b>DİKKAT:</b> {@code spec.marginAmount} (İş Yatırım scrape) ZATEN TL tutardır →
     * FX UYGULANMAZ (çifte-FX olurdu). FX yalnızca fiyattan türetilen oran-bazlı yola uygulanır.
     */
    public BigDecimal marginPosted(BigDecimal qty, BigDecimal avgEntryPrice, ViopContractSpec spec, BigDecimal fxRate) {
        if (qty == null || spec == null) return BigDecimal.ZERO;
        // Scrape'ten gelen gerçek teminat tutarı varsa onu kullan (fiyattan bağımsız, ZATEN TL → FX YOK).
        if (spec.marginAmount() != null && spec.marginAmount().signum() > 0) {
            return qty.multiply(spec.marginAmount())
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        // Aksi halde (YAML statik) oran-bazlı: qty × (giriş × fx) × çarpan × oran.
        if (anyNull(avgEntryPrice, spec.multiplier(), spec.marginRate())) return BigDecimal.ZERO;
        BigDecimal entryTl = avgEntryPrice.multiply(effectiveFx(spec, fxRate));
        return qty.multiply(entryTl).multiply(spec.multiplier()).multiply(spec.marginRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Başlangıç teminatı — per-date FX: USD-kote kontratta giriş fiyatı ALIŞ GÜNÜNÜN kuru
     * ({@code fxEntry}) ile TL'ye çevrilir (teminat alış anında bağlanan tutardır → o günün kuru).
     * {@code spec.marginAmount} (scrape, zaten TL) yolu FX'ten muaftır (değişmez). TRY kontratta
     * {@code effectiveFx}=1 → davranış {@link #marginPosted(BigDecimal, BigDecimal, ViopContractSpec, BigDecimal)}
     * ile AYNI. Tek-FX imzayla geriye tam uyumlu (fxEntry=fxNow geçilirse eşdeğer).
     */
    public BigDecimal marginPostedPerDate(BigDecimal qty, BigDecimal avgEntryPrice,
                                          ViopContractSpec spec, BigDecimal fxEntry) {
        // marginPosted zaten yalnız GİRİŞ fiyatına FX uygular → fxEntry'yi olduğu gibi geçmek yeterli.
        return marginPosted(qty, avgEntryPrice, spec, fxEntry);
    }

    /**
     * Kâr/zarar = fiyat farkı × çarpan × adet × yön. Long için yükselişte +, düşüşte −.
     * Short tam tersi.
     */
    public BigDecimal pnl(BigDecimal qty, BigDecimal avgEntryPrice, BigDecimal currentPrice,
                          ViopContractSpec spec, String direction) {
        return pnl(qty, avgEntryPrice, currentPrice, spec, direction, BigDecimal.ONE);
    }

    /**
     * Kâr/zarar — USD-kote kontratta fiyat farkı {@code fxRate} ile TL'ye çevrilir:
     * {@code qty × ((current − avgEntry) × fx) × multiplier × yön}. TRY kontratta fx etkisizdir.
     *
     * <p>Not (basitleştirme): hem giriş hem güncel fiyat AYNI (anlık) {@code fxRate} ile çevrilir,
     * bu yüzden farka tek çarpan uygulamak yeterli. Per-date FX (girişte o günün kuru) ileride
     * eklenebilir — eurobond Model 1 de canlı kur kullanır.
     */
    public BigDecimal pnl(BigDecimal qty, BigDecimal avgEntryPrice, BigDecimal currentPrice,
                          ViopContractSpec spec, String direction, BigDecimal fxRate) {
        if (anyNull(qty, avgEntryPrice, currentPrice, spec)) return BigDecimal.ZERO;
        return currentPrice.subtract(avgEntryPrice)
                .multiply(effectiveFx(spec, fxRate))
                .multiply(qty)
                .multiply(spec.multiplier())
                .multiply(directionSign(direction))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Kâr/zarar — PER-DATE FX: USD-kote kontratta giriş fiyatı ALIŞ GÜNÜNÜN kuru ({@code fxEntry}),
     * güncel fiyat BUGÜNÜN kuru ({@code fxNow}) ile TL'ye çevrilir → kur hareketinin K/Z'ye katkısı
     * da yansır:
     * <pre>{@code pnl = qty × multiplier × yön × (current×fxNow − avgEntry×fxEntry)}</pre>
     * <p>Tek-FX {@link #pnl(BigDecimal, BigDecimal, BigDecimal, ViopContractSpec, String, BigDecimal)}
     * yalnız fiyat farkını çevirir (kur kazancı kaybolur). TRY kontratta {@code effectiveFx}=1 →
     * {@code (current−avgEntry)} ile tek-FX'le AYNI sonuç (davranış değişmez). fxEntry=fxNow geçilirse
     * eski tek-FX davranışına EŞDEĞER (geriye uyumlu).
     */
    public BigDecimal pnlPerDate(BigDecimal qty, BigDecimal avgEntryPrice, BigDecimal currentPrice,
                                 ViopContractSpec spec, String direction,
                                 BigDecimal fxEntry, BigDecimal fxNow) {
        if (anyNull(qty, avgEntryPrice, currentPrice, spec)) return BigDecimal.ZERO;
        BigDecimal currentTl = currentPrice.multiply(effectiveFx(spec, fxNow));
        BigDecimal entryTl = avgEntryPrice.multiply(effectiveFx(spec, fxEntry));
        return currentTl.subtract(entryTl)
                .multiply(qty)
                .multiply(spec.multiplier())
                .multiply(directionSign(direction))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Günlük (mark-to-market) kâr/zarar — uzlaşma fiyatı kullanır.
     * VİOP'ta günlük netleşmiş nakit akışı.
     */
    public BigDecimal dailyPnl(BigDecimal qty, BigDecimal currentSettlement, BigDecimal prevSettlement,
                               ViopContractSpec spec, String direction) {
        return dailyPnl(qty, currentSettlement, prevSettlement, spec, direction, BigDecimal.ONE);
    }

    /**
     * Günlük (mark-to-market) kâr/zarar — USD-kote kontratta uzlaşma farkı {@code fxRate} ile
     * TL'ye çevrilir: {@code qty × ((settle − prevSettle) × fx) × multiplier × yön}.
     */
    public BigDecimal dailyPnl(BigDecimal qty, BigDecimal currentSettlement, BigDecimal prevSettlement,
                               ViopContractSpec spec, String direction, BigDecimal fxRate) {
        if (anyNull(qty, currentSettlement, prevSettlement, spec)) return BigDecimal.ZERO;
        return currentSettlement.subtract(prevSettlement)
                .multiply(effectiveFx(spec, fxRate))
                .multiply(qty)
                .multiply(spec.multiplier())
                .multiply(directionSign(direction))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Kaldıraç = notional / margin. Risk göstergesi (1₺ teminat ile X₺ pozisyon).
     */
    public BigDecimal leverage(BigDecimal notional, BigDecimal margin) {
        if (notional == null || margin == null || margin.signum() == 0) return BigDecimal.ZERO;
        return notional.divide(margin, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Portföy toplamına katılan "market value": gerçek bağlı sermaye + biriken kâr/zarar.
     * KAYITLI tutar (notional değil) — pasta grafiği bozmaz, dönem getirisi doğru.
     */
    public BigDecimal portfolioContribution(BigDecimal marginPosted, BigDecimal pnl) {
        if (marginPosted == null) marginPosted = BigDecimal.ZERO;
        if (pnl == null) pnl = BigDecimal.ZERO;
        return marginPosted.add(pnl).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static boolean anyNull(Object... os) {
        for (Object o : os) if (o == null) return true;
        return false;
    }
}
