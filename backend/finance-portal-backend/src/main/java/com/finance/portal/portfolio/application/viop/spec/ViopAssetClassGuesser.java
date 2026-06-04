package com.finance.portal.portfolio.application.viop.spec;

import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.AssetClass;

/**
 * VİOP dayanak kodundan/sembolünden varlık sınıfını TAHMİN eder.
 *
 * <p>Yalnızca YAML'da da scrape'te de bulunamayan sözleşmeler için benzer-tür fallback'in
 * doğru oranı seçebilmesi amacıyla kullanılır. Kesin değildir — desen eşleşmezse {@code null}
 * döner (çağıran düz fallback'e düşer).
 *
 * <p>Desenler {@code viop-contract-specs.yml}'deki gerçek kodlardan çıkarıldı:
 * <ul>
 *   <li>FX: döviz çiftleri — {@code USDTRY, EURTRY, EURUSD, GBPUSD, RUBTRY, CNHTRY}</li>
 *   <li>PRECIOUS_METAL: {@code XAU/XAG/XPT/XPD/XCU/GAU} ile başlar (altın/gümüş/platin/paladyum/bakır)</li>
 *   <li>INDEX: {@code XU030, X10XB, XLBNK, XSD25, SASX10}</li>
 *   <li>ENERGY: {@code ELCBAS*} (elektrik baz yük)</li>
 *   <li>RATE: {@code TLREF*}</li>
 *   <li>BOND: {@code TRT*} (DİBS)</li>
 *   <li>SINGLE_STOCK: yukarıdakilere uymayan (hisse kodları)</li>
 * </ul>
 */
public final class ViopAssetClassGuesser {

    private static final String[] FX_QUOTES = {"TRY", "USD", "EUR", "GBP"};
    private static final String[] METAL_PREFIXES = {"XAU", "XAG", "XPT", "XPD", "XCU", "GAU"};

    private ViopAssetClassGuesser() { }

    /**
     * Sembol veya dayanak kodundan varlık sınıfını tahmin eder.
     *
     * @param contractSymbol tam kontrat sembolü (F_USDTRY0626) — code null ise buradan türetilir
     * @param code           çözülmüş dayanak kodu (USDTRY); null olabilir
     * @return tahmini sınıf veya {@code null} (hiçbir desen tutmadı)
     */
    public static AssetClass guess(String contractSymbol, String code) {
        String c = normalize(code);
        if (c == null) {
            c = normalize(stripContractSuffix(contractSymbol));
        }
        if (c == null) return null;

        // RATE — TLREF faiz
        if (c.startsWith("TLREF")) return AssetClass.RATE;
        // BOND — DİBS (TRT ile başlayan tahvil kodları)
        if (c.startsWith("TRT")) return AssetClass.BOND;
        // ENERGY — elektrik
        if (c.startsWith("ELCBAS")) return AssetClass.ENERGY;
        // PRECIOUS_METAL — değerli maden prefix'leri
        for (String p : METAL_PREFIXES) {
            if (c.startsWith(p)) return AssetClass.PRECIOUS_METAL;
        }
        // INDEX — endeks kodları (X ile başlayıp döviz/maden olmayan, ya da SASX)
        if (c.startsWith("SASX") || isIndexCode(c)) return AssetClass.INDEX;
        // FX — döviz çiftleri (6 harf, ikinci 3 harf bilinen kotasyon)
        if (isFxPair(c)) return AssetClass.FX;
        // geri kalan → hisse
        return AssetClass.SINGLE_STOCK;
    }

    /** {@code XU030, XLBNK, X10XB} gibi endeks kodu mu (X ile başlar, döviz/maden değil). */
    private static boolean isIndexCode(String c) {
        if (!c.startsWith("X")) return false;
        // döviz çifti veya maden değilse (onlar yukarıda ele alındı) ve 6-harf-çift değilse endeks say.
        return !isFxPair(c);
    }

    /** {@code USDTRY, EURUSD} — 6 harf, son 3'ü bilinen kotasyon birimi. */
    private static boolean isFxPair(String c) {
        if (c.length() != 6) return false;
        String quote = c.substring(3);
        for (String q : FX_QUOTES) {
            if (quote.equals(q)) return true;
        }
        return false;
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase();
        return t.isEmpty() ? null : t;
    }

    /** {@code F_USDTRY0626} → {@code USDTRY} (kaba: F_ ve sondaki 4 haneli vade at). */
    private static String stripContractSuffix(String symbol) {
        if (symbol == null) return null;
        String s = symbol.trim().toUpperCase();
        if (s.startsWith("F_")) s = s.substring(2);
        // sondaki MMYY (4 rakam) + olası N-variant'ı at
        s = s.replaceAll("(?:N\\d+)?$", "");
        s = s.replaceAll("\\d{4}$", "");
        return s.isEmpty() ? null : s;
    }
}
