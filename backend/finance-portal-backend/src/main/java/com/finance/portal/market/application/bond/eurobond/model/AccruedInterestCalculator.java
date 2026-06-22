package com.finance.portal.market.application.bond.eurobond.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Eurobond <b>birikmiş faiz (accrued interest)</b> ve <b>kirli fiyat (dirty price)</b> hesaplar.
 *
 * <p>Finansal arka plan: piyasada kote edilen fiyat <i>temiz fiyat</i>tır (faiz hariç). Takasta
 * fiilen ödenen/alınan bedel <i>kirli fiyat</i>tır: {@code kirli = temiz + birikmiş_faiz}. Birikmiş
 * faiz, son kupon ödemesinden bu yana geçen sürenin dönem kuponundaki payıdır:
 * <pre>
 *   dönem_kuponu   = yıllık_kupon / ödeme_sıklığı           (100 nominal başına)
 *   birikmiş_faiz  = dönem_kuponu × (geçen_gün / dönem_gün)  (100 nominal başına)
 * </pre>
 * "Geçen gün" ve "dönem gün" {@link DayCountConvention} ile sayılır (para birimine göre seçilir).
 *
 * <p>Veri kaynağı (Business Insider) kupon konvansiyonunu vermez; bu yüzden sonuç <b>tahmini</b>dir.
 * Hesaplama tamamen saf (stateless) — mevcut portföy/değerleme mantığını etkilemez; yalnız ek bilgi
 * üretir.
 *
 * <p>Tüm değerler <b>100 nominal başına</b> (kote ölçeğiyle aynı, %-of-par). Çağıran tarafta
 * {@code mv = qty × kirliFiyat / 100} olarak ölçeklenir (DİBS/Eurobond ile aynı PAR_SCALE=100).
 */
public final class AccruedInterestCalculator {

    private AccruedInterestCalculator() {
        // utility
    }

    /** BI tarih formatı: Amerikan M/d/yyyy (örn. "7/15/2026"). */
    private static final DateTimeFormatter BI_DATE =
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US);

    private static final int SCALE = 6;

    /**
     * Birikmiş faiz + kirli fiyat hesabının sonucu. Hesaplanamadıysa {@link #unavailable()} döner
     * ({@code available=false}); bu durumda çağıran taraf kirli fiyat göstermemeli.
     *
     * @param accruedInterest 100 nominal başına birikmiş faiz (ör. 3.9583)
     * @param dirtyPrice      temiz + birikmiş (ör. 122.9083); cleanPrice null ise null
     * @param dayCount        kullanılan konvansiyon (USD→30/360 vb.)
     * @param periodStart     içinde bulunulan kupon döneminin başı (önceki kupon tarihi)
     * @param periodEnd       içinde bulunulan kupon döneminin sonu (sonraki kupon tarihi)
     * @param available       hesaplama başarılı mı (false → veri eksik/uygunsuz)
     */
    public record AccruedResult(
            BigDecimal accruedInterest,
            BigDecimal dirtyPrice,
            DayCountConvention dayCount,
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean available) {

        public static AccruedResult unavailable() {
            return new AccruedResult(null, null, null, null, null, false);
        }
    }

    /**
     * {@link EurobondDetail} künyesinden, {@code asOf} tarihindeki birikmiş faizi ve kirli fiyatı
     * hesaplar. Gerekli alanlardan biri eksik/ayrıştırılamazsa {@link AccruedResult#unavailable()}.
     *
     * @param detail eurobond künyesi (couponRate, paymentsPerYear, kupon tarihleri, currency, cleanPrice)
     * @param cleanPrice temiz (kote) fiyat — 100 nominal başına; null ise dirty hesaplanmaz ama accrued döner
     * @param asOf hesap tarihi (genelde işlem tarihi ya da bugün)
     */
    public static AccruedResult compute(EurobondDetail detail, BigDecimal cleanPrice, LocalDate asOf) {
        if (detail == null || asOf == null) {
            return AccruedResult.unavailable();
        }
        BigDecimal annualCoupon = parsePercent(detail.getCouponRate());
        int perYear = parsePaymentsPerYear(detail.getPaymentsPerYear());
        if (annualCoupon == null || annualCoupon.signum() <= 0 || perYear <= 0) {
            return AccruedResult.unavailable();
        }
        // Bir kupon ödeme tarihi referansı (BI "Coupon Payment" = sıradaki/temsili ödeme tarihi).
        LocalDate anchor = firstNonNull(
                parseDate(detail.getCouponPaymentDate()),
                parseDate(detail.getFinalCouponDate()),
                parseDate(detail.getCouponStartDate()));
        if (anchor == null) {
            return AccruedResult.unavailable();
        }
        int monthsPerPeriod = 12 / perYear;
        if (monthsPerPeriod <= 0) {
            return AccruedResult.unavailable();
        }

        // asOf'u saran kupon dönemini bul: anchor'dan periyot periyot kaydırarak
        // periodStart <= asOf < periodEnd olacak şekilde [periodStart, periodEnd) aralığını üret.
        LocalDate periodEnd = anchor;
        // İleri: periodEnd asOf'tan büyük olana kadar (en küçük öyle tarih)
        while (!periodEnd.isAfter(asOf)) {
            periodEnd = periodEnd.plusMonths(monthsPerPeriod);
        }
        // Geri: periodEnd asOf'u ilk geçen değere ininceye kadar
        while (periodEnd.minusMonths(monthsPerPeriod).isAfter(asOf)) {
            periodEnd = periodEnd.minusMonths(monthsPerPeriod);
        }
        LocalDate periodStart = periodEnd.minusMonths(monthsPerPeriod);

        DayCountConvention dcc = DayCountConvention.forCurrency(detail.getCurrency());
        long elapsed = dcc.daysBetween(periodStart, asOf);
        long periodDays = dcc.daysBetween(periodStart, periodEnd);
        if (periodDays <= 0) {
            return AccruedResult.unavailable();
        }
        if (elapsed < 0) {
            elapsed = 0;
        }
        if (elapsed > periodDays) {
            elapsed = periodDays;
        }

        BigDecimal periodCoupon = annualCoupon.divide(BigDecimal.valueOf(perYear), SCALE, RoundingMode.HALF_UP);
        BigDecimal accrued = periodCoupon
                .multiply(BigDecimal.valueOf(elapsed))
                .divide(BigDecimal.valueOf(periodDays), SCALE, RoundingMode.HALF_UP);

        BigDecimal dirty = cleanPrice != null
                ? cleanPrice.add(accrued).setScale(SCALE, RoundingMode.HALF_UP)
                : null;

        return new AccruedResult(accrued, dirty, dcc, periodStart, periodEnd, true);
    }

    // ── Parsing yardımcıları (BI ham string formatları) ─────────────────────

    /** "11.875%" / "5,200%" → 11.875 / 5.200. Null/boş/ayrıştırılamaz → null. */
    static BigDecimal parsePercent(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("%", "").trim();
        if (s.isEmpty()) return null;
        // BI bazen virgüllü ondalık verir ("5,200"); nokta ondalık standardına çevir.
        // Binlik ayırıcı kupon oranında beklenmez (tek haneli/onlu yüzde).
        s = s.replace(",", ".");
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "2,0" / "2.0" / "1" → 2 / 2 / 1. Ayrıştırılamaz → 0. */
    static int parsePaymentsPerYear(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().replace(",", ".");
        if (s.isEmpty()) return 0;
        try {
            return new BigDecimal(s).setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return 0;
        }
    }

    /** BI M/d/yyyy → LocalDate. Null/boş/ayrıştırılamaz → null. */
    static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s, BI_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }
}
