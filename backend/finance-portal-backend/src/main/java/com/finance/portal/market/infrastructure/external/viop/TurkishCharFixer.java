package com.finance.portal.market.infrastructure.external.viop;

/**
 * UTF-8 karakterlerin Latin-1 (ISO-8859-1) olarak yanlış decode edilmesinden
 * kaynaklanan bozuk Türkçe karakter dizilerini düzeltir.
 *
 * Akbank HTML UTF-8 döndürür, ancak Spring RestTemplate bazen Latin-1 ile
 * decode eder. Bu durumda Türkçe karakterler şu şekilde bozulur:
 *
 *   ğ (U+011F, UTF-8: C4 9F) → Ä + \u009F → "Ä\u009F"
 *   ş (U+015F, UTF-8: C5 9F) → Å + \u009F → "Å\u009F"
 *   ü (U+00FC, UTF-8: C3 BC) → Ã + ¼     → "Ã¼"
 *   ö (U+00F6, UTF-8: C3 B6) → Ã + ¶     → "Ã¶"
 *   ç (U+00E7, UTF-8: C3 A7) → Ã + §     → "Ã§"
 *   ı (U+0131, UTF-8: C4 B1) → Ä + ±     → "Ä±"
 *   İ (U+0130, UTF-8: C4 B0) → Ä + °     → "Ä°"
 *   Ğ (U+011E, UTF-8: C4 9E) → Ä + \u009E
 *   Ş (U+015E, UTF-8: C5 9E) → Å + \u009E
 *   Ü (U+00DC, UTF-8: C3 9C) → Ã + \u009C
 *   Ö (U+00D6, UTF-8: C3 96) → Ã + \u0096
 *   Ç (U+00C7, UTF-8: C3 87) → Ã + \u0087
 *
 * Ekranda "AĞYu" görünmesi: Windows-1252 decode'da C4=Ä, 9E=Ž → "AÄŽu"
 * veya farklı bir code page'de Ğ+Y şeklinde bozulma.
 * Bu sınıf tüm olası bozuk varyantları kapsayacak şekilde tasarlanmıştır.
 */
public final class TurkishCharFixer {

    private TurkishCharFixer() {}

    /**
     * Bozuk encoding'i düzeltir.
     * Strateji:
     * 1. Bilinen bozuk pattern'leri string replace ile düzelt (whitespace içerenler dahil)
     * 2. ISO-8859-1 → UTF-8 re-encode dene
     * 3. Windows-1252 → UTF-8 re-encode dene
     */
    public static String fix(String s) {
        if (s == null || s.isEmpty()) return s;

        // 1. Önce bilinen pattern'leri replace et
        String fixed = fixKnownPatterns(s);

        // 2. ISO-8859-1 → UTF-8 re-encode dene
        try {
            byte[] bytes = fixed.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            String reencoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (!reencoded.contains("\uFFFD")) {
                return reencoded;
            }
        } catch (Exception ignored) {}

        // 3. Windows-1252 → UTF-8 re-encode dene
        try {
            byte[] bytes = fixed.getBytes(java.nio.charset.Charset.forName("windows-1252"));
            String reencoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (!reencoded.contains("\uFFFD")) {
                return reencoded;
            }
        } catch (Exception ignored) {}

        return fixed;
    }

    /**
     * Bilinen bozuk UTF-8 → Latin-1/Windows-1252 decode pattern'lerini düzeltir.
     *
     * "Ağustos" için olası bozuk varyantlar:
     *   - ISO-8859-1:    "AÄ\u009Fu" (C4 9F = ğ)
     *   - Windows-1252:  "AÄŽu"      (C4 8E = Ž, 9E = ž → ğ için C4 9F ama 1252'de 9E=ž)
     *   - Ekranda görünen "AĞYu": Ğ=U+011E, Y muhtemelen bozuk 2. byte
     *     → C4 9E windows-1252'de: C4=Ä, 9E=ž → "Äžu" veya farklı render
     *     → Gerçek kaynak: token içinde whitespace/newline ile bölünmüş olabilir
     */
    private static String fixKnownPatterns(String s) {
        return s
                // ===== ğ / Ğ =====
                // ISO-8859-1: C4=Ä, 9F=\u009F
                .replace("Ä\u009Fu", "ğu")
                .replace("Ä\u009F",  "ğ")
                // Windows-1252: C4=Ä, 9E=ž (Ğ için C4 9E)
                .replace("Äžu",      "ğu")   // küçük ğ (C4 9F yanlış 1252 decode)
                .replace("Äž",       "ğ")
                // Büyük Ğ: C4 9E → ISO: Ä+\u009E, Win1252: Ä+ž
                .replace("Ä\u009E",  "Ğ")
                .replace("AÄ\u009Eu","Ağu")   // "Ağustos" büyük Ğ varyantı
                .replace("AÄ\u009E", "Ağ")
                // Ekranda "AĞYu" görünümü — Ğ (U+011E) + Y bozuk render
                // Kaynak muhtemelen whitespace ile bölünmüş: "AĞ" + "\n" + "u"
                // veya Windows-1252'de 9E=Ž → "AÄŽu"
                .replace("AĞYu",     "Ağu")   // direkt görünen bozuk string
                .replace("AĞY",      "Ağ")
                .replace("ĞYu",      "ğu")
                .replace("ĞY",       "ğ")
                // Satır sonu içeren bozuk pattern'ler
                .replace("AÄ\u009F\nu",  "Ağu")
                .replace("AÄ\u009F\ru",  "Ağu")
                .replace("AÄ\u009F\r\nu","Ağu")
                .replace("\u00C3\nÅ\u00B8u", "ğu")
                .replace("\u00C3\rÅ\u00B8u", "ğu")
                .replace("\u00C3\r\nÅ\u00B8u", "ğu")
                .replace("\u00C3 \u00C5\u00B8u", "ğu")
                .replace("\u00C3 \u00C5\u00B8", "ğ")
                // Ay kısaltmalarına özel (token parse sonrası whitespace normalize edilmiş)
                .replace("A\u00C4\u009Fu", "Ağu")
                .replace("A\u00C4\u009F",  "Ağ")

                // ===== ş / Ş =====
                .replace("Å\u009Fu", "şu")
                .replace("Å\u009F",  "ş")
                .replace("Å\u009E",  "Ş")
                .replace("\u00C5\u009Eub", "Şub")
                .replace("\u00C5\u009Eu",  "Şu")
                // "Aşub" → "Şub" (Ş bozuk encode: önüne "A" eklenmiş)
                .replace("Aşub", "Şub")
                .replace("Aşu",  "Şu")
                // "Åub" / "Âub" → "Şub" (Şubat — farklı code page bozulmaları)
                .replace("Åub",  "Şub")
                .replace("Åu",   "Şu")
                .replace("Âub",  "Şub")
                .replace("Âu",   "Şu")

                // ===== ü / Ü =====
                .replace("Ã¼",       "ü")
                .replace("Ã\u009C",  "Ü")

                // ===== ö / Ö =====
                .replace("Ã¶",       "ö")
                .replace("Ã\u0096",  "Ö")

                // ===== ç / Ç =====
                .replace("Ã§",       "ç")
                .replace("Ã\u0087",  "Ç")

                // ===== ı / İ =====
                .replace("Ä±",       "ı")
                .replace("Ä°",       "İ")
                ;
    }
}
