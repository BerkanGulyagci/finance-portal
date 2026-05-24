package com.finance.portal.alarm.domain;

import com.finance.portal.common.domain.AssetType;

import java.util.Locale;

/**
 * Bir alarmın eşik/değer gösteriminde kullanılacak para birimi sembolü.
 * Alarm motorunun fiyat probe'unun döndüğü birimle uyumludur:
 * BIST hisse/VİOP, döviz (X/TRY), fon, kripto (TRY bazlı), altın (TRY) → ₺;
 * ABD hissesi / Yahoo vadeli / Yahoo emtia → $; DİBS gösterge değeri birimsiz.
 */
public final class AlarmCurrency {

    private AlarmCurrency() {
    }

    public static String symbolFor(AssetType type, String symbol) {
        if (type == null) {
            return "";
        }
        String s = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case FX, FUND, CRYPTO, GOLD -> "₺";
            case STOCK -> s.endsWith(".IS") ? "₺" : "$";       // BIST → ₺, ABD → $
            case FUTURE -> s.endsWith("=F") ? "$" : "₺";       // Yahoo vadeli → $, VİOP → ₺
            case COMMODITY -> s.contains("TRY") ? "₺" : "$";   // BIST gümüş TRY → ₺, Yahoo → $
            case BOND -> "";                                    // gösterge değeri (birimsiz)
        };
    }
}
