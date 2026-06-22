package com.finance.portal.market.application.bond.eurobond.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Tahvil birikmiş faiz (accrued interest) hesabında kullanılan gün-sayım konvansiyonu.
 *
 * <p>Eurobond birikmiş faizi = dönem_kuponu × (geçen_gün / dönemdeki_gün). "Geçen gün" ve
 * "dönemdeki gün" bu konvansiyona göre sayılır. Tek bir evrensel standart YOKTUR; her tahvilin
 * ihraç şartında (prospectus) belirlenir. Veri kaynağımız (Business Insider) konvansiyonu
 * sunmadığı için <b>para birimine göre tahmin</b> ederiz (piyasada %90+ belirleyici):
 * <ul>
 *   <li>USD eurobond → {@link #THIRTY_360} (US/30E-360 — piyasa varsayılanı)</li>
 *   <li>EUR eurobond → {@link #ACT_ACT}</li>
 *   <li>JPY eurobond → {@link #ACT_365}</li>
 *   <li>diğer / bilinmeyen → {@link #ACT_ACT} (güvenli varsayılan)</li>
 * </ul>
 * Bu yüzden hesaplanan birikmiş faiz <b>tahmini</b>dir (UI'da "tahmini" etiketiyle gösterilir).
 */
public enum DayCountConvention {

    /**
     * 30E/360 (Eurobond Basis). Her ay 30 gün, yıl 360 gün kabul edilir. USD eurobondların
     * ezici çoğunluğu bunu kullanır. Ay-sonu kuralı: gün 31 ise 30'a indirgenir (Avrupa kuralı,
     * her iki uç için de).
     */
    THIRTY_360 {
        @Override
        public long daysBetween(LocalDate start, LocalDate end) {
            int d1 = Math.min(start.getDayOfMonth(), 30);
            int d2 = Math.min(end.getDayOfMonth(), 30);
            return 360L * (end.getYear() - start.getYear())
                    + 30L * (end.getMonthValue() - start.getMonthValue())
                    + (d2 - d1);
        }
    },

    /** ACT/ACT — gerçek takvim günleri. EUR cinsi tahvil standardı. */
    ACT_ACT {
        @Override
        public long daysBetween(LocalDate start, LocalDate end) {
            return ChronoUnit.DAYS.between(start, end);
        }
    },

    /** ACT/365 — gerçek günler, yıl 365 sabit. JPY ve bazı piyasalar. */
    ACT_365 {
        @Override
        public long daysBetween(LocalDate start, LocalDate end) {
            return ChronoUnit.DAYS.between(start, end);
        }
    };

    /**
     * İki tarih arasındaki gün sayısını bu konvansiyona göre döndürür ({@code start} dahil,
     * {@code end} hariç mantığı çağırana bırakılır — fark olarak hesaplanır).
     */
    public abstract long daysBetween(LocalDate start, LocalDate end);

    /**
     * Para birimine göre piyasa-varsayılanı konvansiyonu seçer. {@code currency} null/boş ya da
     * tanınmıyorsa {@link #ACT_ACT} döner (güvenli varsayılan).
     */
    public static DayCountConvention forCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return ACT_ACT;
        }
        switch (currency.trim().toUpperCase()) {
            case "USD":
                return THIRTY_360;
            case "JPY":
                return ACT_365;
            case "EUR":
            default:
                return ACT_ACT;
        }
    }
}
