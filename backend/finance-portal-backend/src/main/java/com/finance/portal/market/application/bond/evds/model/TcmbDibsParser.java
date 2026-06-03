package com.finance.portal.market.application.bond.evds.model;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TCMB {@code dibs1.txt} (Devlet İç Borçlanma Senetleri günlük gösterge değerleri) dosyasını parse
 * eder ve her ISIN'i {@link DibsSectionFamily}'ye (otoriter aile) eşler.
 *
 * <p>Dosya bölümlere ayrılmıştır; her bölüm bir aileyi temsil eder. Bölüm başlıkları numaralıdır
 * ({@code "1- ..."}, {@code "2- ..."}) ve aile, başlık metnindeki anahtar kelimeden saptanır
 * (İSKONTOLU / KUPONLU / TÜKETİCİ / ALTINA / AVRO / DOLARI / GECELİK / LİKİDİTE / KİRA). Veri
 * satırları sabit-genişlik kolonludur ve {@code dd.mm.yyyy} ile başlar:
 * <pre>17.03.2027 11B             TRB170327T15          287         75.194</pre>
 *
 * <p>{@code A)BORÇLANMA SENETLERİ} (Part A, DT) ile {@code B)KİRA SERTİFİKALARI} (Part B, sukuk)
 * ayrımı izlenir; Part B'de aynı anahtar kelimeler kira-varyantlarına ({@code LEASE_*}) eşlenir.
 *
 * <p>Saf/yan etkisiz — HTTP veya cache içermez. Erişim/önbellek için
 * {@code TcmbDibsClassificationService}.
 */
public final class TcmbDibsParser {

    private TcmbDibsParser() {
        // utility
    }

    /** Veri satırı: {@code dd.mm.yyyy <CBRT kod=group(1)> <ISIN(TR...)=group(2)> ...} */
    private static final Pattern DATA_ROW =
            Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}\\s+(\\S+)\\s+(TR\\S+)\\b");

    /** Numaralı bölüm başlığı: {@code "1- ..."}, {@code "4- (b) ..."} */
    private static final Pattern NUM_HEADER = Pattern.compile("^\\s*\\d+-\\s");

    /**
     * dibs1.txt içeriğini ISIN → {@link BondCategory} haritasına çevirir. <b>Aile</b> ISIN'in
     * göründüğü bölümden (otoriter); <b>strip alt-türü</b> CBRT kodundaki işaretten (yoksa ISIN
     * suffix) — {@link BondClassifier#classifyFromFamily(DibsSectionFamily, String, String)}.
     * Tanınmayan/başlıksız satırlar atlanır.
     */
    public static Map<String, BondCategory> parse(String content) {
        Map<String, BondCategory> map = new HashMap<>();
        if (content == null || content.isBlank()) {
            return map;
        }
        boolean partB = false;
        DibsSectionFamily current = null;

        for (String line : content.split("\\r?\\n")) {
            // Part A (DT) → Part B (Kira sertifikası) geçişi
            if (line.contains("B)KİRA") || line.contains("B)Lease")) {
                partB = true;
                continue;
            }
            if (NUM_HEADER.matcher(line).find()) {
                DibsSectionFamily fam = detectFamily(line, partB);
                if (fam != null) {
                    current = fam;
                }
                continue;
            }
            if (current == null) {
                continue;
            }
            Matcher m = DATA_ROW.matcher(line);
            if (m.find()) {
                String cbrt = m.group(1);
                String isin = m.group(2).toUpperCase(Locale.ROOT);
                map.put(isin, BondClassifier.classifyFromFamily(current, cbrt, isin));
            }
        }
        return map;
    }

    /**
     * Bölüm başlığı metninden aileyi saptar. Başlıklar tamamen büyük harf olduğundan doğrudan
     * alt-dize eşlemesi yapılır. Sıra önemlidir: ayırt edici aileler (altın/döviz/gecelik/
     * likidite/tüketici) genel "KİRA"dan önce kontrol edilir.
     */
    private static DibsSectionFamily detectFamily(String header, boolean partB) {
        String h = header.toUpperCase(new Locale("tr", "TR"));
        if (h.contains("ALTIN")) {
            return partB ? DibsSectionFamily.LEASE_GOLD : DibsSectionFamily.GOLD;
        }
        if (h.contains("AVRO") || h.contains("AMER") || h.contains("DOLAR")) {
            return partB ? DibsSectionFamily.LEASE_FX : DibsSectionFamily.FX;
        }
        if (h.contains("GECEL")) {
            return DibsSectionFamily.TLREF;
        }
        if (h.contains("LİKİD") || h.contains("LIKID")) {
            return DibsSectionFamily.LIQUIDITY;
        }
        if (h.contains("TÜKET") || h.contains("TUKET")) {
            return partB ? DibsSectionFamily.LEASE_INFLATION : DibsSectionFamily.INFLATION;
        }
        if (h.contains("SKONTO")) {
            return DibsSectionFamily.ZERO_COUPON;
        }
        if (h.contains("KUPONLU")) {
            return DibsSectionFamily.FIXED_COUPON;
        }
        if (partB && (h.contains("KİRA") || h.contains("KIRA"))) {
            return DibsSectionFamily.LEASE;
        }
        return null;
    }
}
