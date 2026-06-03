package com.finance.portal.market.application.bond.evds.model;

import java.util.Locale;

/**
 * DİBS / Kira Sertifikası finansal kategorisini ISIN + CBRT (TCMB) koduna göre belirler.
 *
 * <p>Algoritma 3 katmanlı:
 * <ol>
 *   <li><b>CBRT kod prefix analizi</b> — TCMB'nin resmi sınıflandırma sinyali (en kuvvetli).
 *       Kalıplar TCMB {@code dibs1.txt} yayınından gözlemle çıkarıldı.</li>
 *   <li><b>ISIN suffix harfi</b> (T / A / K / F) — strip / yabancı para ayrımı için.</li>
 *   <li><b>Fallback</b> — ISIN prefix (TRB → bono, TRT → tahvil) + suffix.</li>
 * </ol>
 *
 * <p>CBRT kod kalıpları (gözlemlenmiş):
 * <ul>
 *   <li>{@code 10B}, {@code 11B} — Kuponsuz Hazine Bonosu (Section 1)</li>
 *   <li>{@code 12T} — Kuponsuz Devlet Tahvili (Section 1)</li>
 *   <li>{@code 121T2} (suffix yok) — Sabit kuponlu tam DT (Section 2-T)</li>
 *   <li>{@code 121T2A...} — Sabit kuponlu DT ana para stripi (Section 2-A)</li>
 *   <li>{@code 121T2K...} — Sabit kuponlu DT kupon stripi (Section 2-K)</li>
 *   <li>{@code 61T...} (örn. {@code 61T4DK...}) — TÜFE-endeksli DT (Section 3)</li>
 * </ul>
 */
public final class BondClassifier {

    private BondClassifier() {
        // utility
    }

    /**
     * Tek bir kıymetin {@link BondCategory}'sini belirler.
     * Geriye uyumlu alias — datagroup bilinmiyorsa.
     */
    public static BondCategory classify(String isin, String cbrtCode) {
        return classify(isin, cbrtCode, null);
    }

    /**
     * Tek bir kıymetin {@link BondCategory}'sini belirler.
     *
     * @param isin          ISIN kodu (örn. "TRT030626K26"), trim/uppercase otomatik
     * @param cbrtCode      TCMB CBRT kodu ({@link EvdsSeriesInfo#parseCbrtCode()}), null olabilir
     * @param datagroupCode EVDS data group (örn. {@code bie_pyks} Kira Sertifikası, {@code bie_pydibs} DİBS)
     */
    public static BondCategory classify(String isin, String cbrtCode, String datagroupCode) {
        String code = cbrtCode == null ? "" : cbrtCode.trim().toUpperCase(Locale.ROOT);
        String sym = isin == null ? "" : isin.trim().toUpperCase(Locale.ROOT);
        String dg = datagroupCode == null ? "" : datagroupCode.trim().toLowerCase(Locale.ROOT);

        // 0) Kira Sertifikası — iki sinyal:
        //    (a) data group bie_pyks
        //    (b) ISIN prefix "TRD" (TC Devlet Kira Sertifikası — TRT yerine TRD)
        boolean isKiraDg = dg.equals("bie_pyks") || dg.contains("kira") || dg.contains("pyks");
        boolean isKiraIsin = sym.startsWith("TRD");
        if (isKiraDg || isKiraIsin) {
            // Alt-kategori — DK (TÜFE-endeksli reel kira), ALT/AU (altın), USD/F (yabancı para)
            if (code.contains("ALT") || code.contains("AU")) return BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE;
            if (code.contains("USD") || sym.matches(".*[A-Z]F\\d+")) return BondCategory.FX_LEASE_CERTIFICATE;
            if (code.contains("DK") || code.contains("TF")) return BondCategory.INFLATION_INDEXED_LEASE_CERTIFICATE;
            return BondCategory.LEASE_CERTIFICATE;
        }

        // 0a) ISIN F-suffix → Yabancı Para Cinsli DT (CBRT kodu 12T/121T2 kalıbı olsa bile öncelikli).
        // TCMB Section 5/6 EUR/USD DT'leri ISIN'de TRT...F... formatında, CBRT kodu nominal DT'ye benzer.
        char isinSuffixEarly = lastIsinLetterBeforeDigits(sym);
        if (isinSuffixEarly == 'F' && sym.startsWith("TRT")) {
            return BondCategory.FX_DENOMINATED_BOND;
        }

        // 1) TÜFE-endeksli — birden çok CBRT kod kalıbı:
        //    - "61T..." (klasik prefix)
        //    - "121T2D..." (alternate kuponlu TÜFE-endeksli)
        //    - "121D2..." (D2 prefix)
        boolean isInflation = code.startsWith("61T")
                || code.startsWith("121T2D")
                || code.startsWith("121D2")
                || code.startsWith("121T2A") && code.contains("D")  // edge case: anapara stripi TÜFE
                || (code.startsWith("12") && code.contains("DK"));
        if (isInflation) {
            char suffix = lastIsinLetterBeforeDigits(sym);
            return switch (suffix) {
                case 'A' -> BondCategory.INFLATION_PRINCIPAL_STRIP;
                case 'K' -> BondCategory.INFLATION_COUPON_STRIP;
                default  -> BondCategory.INFLATION_INDEXED_BOND;
            };
        }

        // 2a) Altına dayalı senet (TCMB Section 4-b) — CBRT kalıbı \d+TA\d+
        // Gözlenmiş örnekler: 12TA2, 18TA2, 24TA2. Birim "1 adet = 1 gram has altın",
        // değerler binlerce TL (1223 reel değil — altın gram TL).
        if (code.matches("\\d+TA\\d+")) {
            return BondCategory.GOLD_INDEXED_BOND;
        }

        // 2) Sabit kuponlu DT + stripleri (121T2 ailesi)
        if (code.startsWith("121T2A")) return BondCategory.PRINCIPAL_STRIP;
        if (code.startsWith("121T2K")) return BondCategory.COUPON_STRIP;
        if (code.startsWith("121T2"))  return BondCategory.FIXED_COUPON_BOND;

        // 3) Kuponsuz Hazine Bonosu
        if (code.startsWith("10B") || code.startsWith("11B")) {
            return BondCategory.ZERO_COUPON_BILL;
        }

        // 4) Kuponsuz Devlet Tahvili (Section 1, CBRT 12T)
        if (code.startsWith("12T")) {
            return BondCategory.ZERO_COUPON_BOND;
        }

        // 5) Fallback: ISIN prefix + suffix
        if (sym.startsWith("TRB")) return BondCategory.ZERO_COUPON_BILL;
        if (sym.startsWith("TRT")) {
            char suffix = lastIsinLetterBeforeDigits(sym);
            return switch (suffix) {
                case 'A' -> BondCategory.PRINCIPAL_STRIP;
                case 'K' -> BondCategory.COUPON_STRIP;
                case 'F' -> BondCategory.FX_DENOMINATED_BOND;
                default  -> BondCategory.FIXED_COUPON_BOND;
            };
        }

        return BondCategory.UNKNOWN;
    }

    /** CBRT kodu bilinmeyen çağrılar için ISIN-suffix tabanlı kısayol. */
    public static BondCategory classifyFromFamily(DibsSectionFamily family, String isin) {
        return classifyFromFamily(family, null, isin);
    }

    /**
     * Otoriter {@link DibsSectionFamily} (TCMB dibs1.txt bölümü) + strip işaretinden kesin
     * {@link BondCategory}'yi üretir.
     *
     * <p><b>Aile</b> TCMB bölümünden gelir (kod tahmini değil). <b>Strip alt-türü</b> (ana para /
     * kupon / tam) önce <b>CBRT kodundaki işaretten</b> ({@code ...A}=ana para, {@code ...K}=kupon —
     * TCMB'nin kendi otoriter kodu), bulunamazsa <b>ISIN son harfinden</b> ({@code A}/{@code K}/diğer)
     * belirlenir. CBRT önceliği, {@code "TRT160926KA0"} gibi tek-harf konvansiyonuna uymayan ISIN'lerde
     * ("KA" → basit kural yanlışlıkla 'A' okur) doğru olan tek sinyaldir. Strip ayrımları korunur;
     * sadece "hangi aile" + "hangi strip" otoriter kaynaktan gelir.
     */
    public static BondCategory classifyFromFamily(DibsSectionFamily family, String cbrtCode, String isin) {
        if (family == null) {
            return BondCategory.UNKNOWN;
        }
        String sym = isin == null ? "" : isin.trim().toUpperCase(Locale.ROOT);
        char strip = stripMarkerFromCbrt(cbrtCode);
        if (strip == '\0') {
            strip = lastIsinLetterBeforeDigits(sym);
        }
        return switch (family) {
            case ZERO_COUPON -> sym.startsWith("TRB")
                    ? BondCategory.ZERO_COUPON_BILL
                    : BondCategory.ZERO_COUPON_BOND;
            case FIXED_COUPON -> switch (strip) {
                case 'A' -> BondCategory.PRINCIPAL_STRIP;
                case 'K' -> BondCategory.COUPON_STRIP;
                default  -> BondCategory.FIXED_COUPON_BOND;
            };
            case INFLATION -> switch (strip) {
                case 'A' -> BondCategory.INFLATION_PRINCIPAL_STRIP;
                case 'K' -> BondCategory.INFLATION_COUPON_STRIP;
                default  -> BondCategory.INFLATION_INDEXED_BOND;
            };
            case GOLD            -> BondCategory.GOLD_INDEXED_BOND;
            case FX              -> BondCategory.FX_DENOMINATED_BOND;
            case TLREF           -> BondCategory.TLREF_INDEXED_BOND;
            case LIQUIDITY       -> BondCategory.ZERO_COUPON_BILL;
            case LEASE           -> BondCategory.LEASE_CERTIFICATE;
            case LEASE_INFLATION -> BondCategory.INFLATION_INDEXED_LEASE_CERTIFICATE;
            case LEASE_GOLD      -> BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE;
            case LEASE_FX        -> BondCategory.FX_LEASE_CERTIFICATE;
        };
    }

    /**
     * CBRT kodundaki strip işareti: sondaki rakamlar atıldıktan sonra son harf {@code 'A'} (ana
     * para stripi) ya da {@code 'K'} (kupon stripi) ise onu döner; aksi halde {@code '\0'} (belirgin
     * işaret yok → çağıran ISIN suffix'ine düşer). Örn. {@code 121T2A}→A, {@code 24T2K1150328}→K,
     * {@code 121T2D}/{@code 121TDOZ}→yok (tam tahvil).
     */
    private static char stripMarkerFromCbrt(String cbrtCode) {
        if (cbrtCode == null) {
            return '\0';
        }
        String c = cbrtCode.trim().toUpperCase(Locale.ROOT);
        int end = c.length();
        while (end > 0 && Character.isDigit(c.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            return '\0';
        }
        char last = c.charAt(end - 1);
        return (last == 'A' || last == 'K') ? last : '\0';
    }

    /**
     * Kategoriye uygun nominal para birimi.
     *
     * <p>Tier 1/2 TL'dir. FX kategorisinde USD/EUR ayrımı kesin yapılamadığı için
     * varsayılan USD döner (Tier 3'te EVDS detail/ISIN'e göre kesinleştirilecek).
     */
    public static BondCurrency currencyFor(BondCategory category) {
        if (category == null) return BondCurrency.TRY;
        return switch (category) {
            case GOLD_INDEXED_BOND, GOLD_INDEXED_LEASE_CERTIFICATE -> BondCurrency.GOLD;
            case FX_DENOMINATED_BOND, FX_LEASE_CERTIFICATE -> BondCurrency.USD;
            default -> BondCurrency.TRY;
        };
    }

    /**
     * ISIN'in son rakam grubu öncesindeki son harfi döndürür.
     * <p>Tipik DİBS ISIN formatı: {@code TR[BT] dddddd L dd} → prefix(3) + 6 rakam + 1 harf + 2 rakam.
     * <ul>
     *   <li>{@code "TRT030626K26"} → {@code 'K'}</li>
     *   <li>{@code "TRT240227T17"} → {@code 'T'}</li>
     *   <li>{@code "TRT110827A17"} → {@code 'A'}</li>
     * </ul>
     */
    public static char lastIsinLetterBeforeDigits(String isin) {
        if (isin == null || isin.length() < 5) return ' ';
        int i = isin.length() - 1;
        while (i >= 0 && Character.isDigit(isin.charAt(i))) i--;
        return i >= 0 ? isin.charAt(i) : ' ';
    }
}
