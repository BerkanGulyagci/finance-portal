package com.finance.portal.market.application.viop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Akbank VİOP sözleşme adından İş Yatırım endeks kodunu üretir.
 *
 * <p>Tüm vadeli sözleşmeler: {@code F_{UNDERLYING}{MMYY}}</p>
 *
 * <p>Opsiyonlar (Call/Put içerenler) desteklenmez.</p>
 */
@Component
public class ViopIndexCodeMapper {

    private static final Logger log = LoggerFactory.getLogger(ViopIndexCodeMapper.class);

    /**
     * Vadeli sözleşme adı regex'i.
     * Akbank formatı: "{UNDERLYING} ({DD} {MonthTR} {YY}) Vadeli ..."
     * Ay kısaltması bozuk encoding içerebileceğinden geniş pattern kullanılıyor.
     *
     * Grup 1: underlying sembol
     * Grup 2: gün (2 rakam)
     * Grup 3: ay kısaltması (herhangi karakter — bozuk encoding dahil)
     * Grup 4: yıl (2 rakam)
     */
    private static final Pattern FUTURES_PATTERN = Pattern.compile(
            // 4..8 — ELCBAS05 / ELCBASQ3 gibi vade prefix'inde kodlanmış 8 karakterli sembolleri de kapsar
            "^([A-ZÇĞİÖŞÜ0-9]{4,8})\\s*\\((\\d{2})\\s+(\\S+)\\s+(\\d{2})\\).*(?i)vadeli",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /**
     * ELCBAS aylık sözleşmesi — "ELCBAS{MM} (DD Ay YY) Vadeli" formatı. Vade ayı/yılı
     * sembol prefix'inde değil parantezde gelir. Ay token'ı bu parantezden çözülür.
     */
    private static final Pattern ELCBAS_MONTHLY_PATTERN = Pattern.compile(
            "^ELCBAS\\d{2}\\s*\\(\\d{2}\\s+(\\S+)\\s+(\\d{2})\\).*(?i)vadeli",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /** ELCBAS quarterly — "ELCBASQ{1-4} (DD Ay YY) Vadeli". Vade quarter prefix'te, yıl parantezde. */
    private static final Pattern ELCBAS_QUARTERLY_PATTERN = Pattern.compile(
            "^ELCBASQ(\\d)\\s*\\(\\d{2}\\s+\\S+\\s+(\\d{2})\\).*(?i)vadeli",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /** ELCBAS yıllık — "ELCBASY (DD Ay YY) Vadeli" — vade yılı sadece YY. */
    private static final Pattern ELCBAS_YEARLY_PATTERN = Pattern.compile(
            "^ELCBASY\\s*\\(\\d{2}\\s+\\S+\\s+(\\d{2})\\).*(?i)vadeli",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    /**
     * Akbank kısa adı → İş Yatırım underlier kodu. Bazı Akbank kısa adları İş Yatırım
     * kütüğündeki underlier ile birebir aynı değil — örn. GAUTRY (Akbank) = XAUTRYM (İş Yatırım,
     * 1 gr mini-altın TL). İş Yatırım grafik endpoint'i F_GAUTRY0626 sorgusuna boş döner, F_XAUTRYM0626
     * sorgusuna gerçek veri döner.
     */
    private static final Map<String, String> AKBANK_TO_ISYATIRIM_UNDERLIER = Map.of(
            "GAUTRY", "XAUTRYM"
    );

    private static String aliasUnderlying(String akbankUnderlier) {
        if (akbankUnderlier == null) return null;
        return AKBANK_TO_ISYATIRIM_UNDERLIER.getOrDefault(akbankUnderlier, akbankUnderlier);
    }

    /** Opsiyon sözleşmesi tanıma — "Call" veya "Put" içerir */
    private static final Pattern OPTION_PATTERN = Pattern.compile(
            "(?i)(Call|Put)", Pattern.UNICODE_CHARACTER_CLASS
    );

    /** Türkçe ay kısaltması → 2 haneli ay numarası */
    private static final Map<String, String> MONTH_MAP = Map.ofEntries(
            Map.entry("Oca", "01"),
            Map.entry("Şub", "02"),
            Map.entry("Mar", "03"),
            Map.entry("Nis", "04"),
            Map.entry("May", "05"),
            Map.entry("Haz", "06"),
            Map.entry("Tem", "07"),
            Map.entry("Ağu", "08"),
            Map.entry("Eyl", "09"),
            Map.entry("Eki", "10"),
            Map.entry("Kas", "11"),
            Map.entry("Ara", "12")
    );

    /** Ay kodu → temiz Türkçe kısaltma (bozuk görünüm adını onarmak için). */
    private static final Map<String, String> CODE_TO_ABBREV = Map.ofEntries(
            Map.entry("01", "Oca"), Map.entry("02", "Şub"), Map.entry("03", "Mar"),
            Map.entry("04", "Nis"), Map.entry("05", "May"), Map.entry("06", "Haz"),
            Map.entry("07", "Tem"), Map.entry("08", "Ağu"), Map.entry("09", "Eyl"),
            Map.entry("10", "Eki"), Map.entry("11", "Kas"), Map.entry("12", "Ara")
    );

    /**
     * Yalnızca ASCII harflerine indirgenmiş ay iskeleti → ay kodu. Türkçe harf (Ş, ğ)
     * çıkarıldığında ay kısaltmalarının ASCII iskeletleri tekildir (örn. Şub→"ub",
     * Ağu→"au"), bu yüzden harf nasıl bozulursa bozulsun (U+FFFD, Â, Å, …) doğru ayı verir.
     */
    private static final Map<String, String> SKELETON_MONTH_MAP = Map.ofEntries(
            Map.entry("oca", "01"), Map.entry("ub", "02"), Map.entry("mar", "03"),
            Map.entry("nis", "04"), Map.entry("may", "05"), Map.entry("haz", "06"),
            Map.entry("tem", "07"), Map.entry("au", "08"), Map.entry("eyl", "09"),
            Map.entry("eki", "10"), Map.entry("kas", "11"), Map.entry("ara", "12")
    );

    /** Sözleşme adındaki "(GG Ay YY)" tarih parantezi. */
    private static final Pattern DATE_PAREN_PATTERN =
            Pattern.compile("\\(\\s*\\d{1,2}\\s+(\\S+?)\\s+\\d{2}\\s*\\)");

    /**
     * Ay token'ını yalnızca ASCII harflerine indirgeyerek ay koduna çözer.
     * Akbank ham HTML'i Türkçe harfi (Ş/ğ) bazen U+FFFD dahil çeşitli şekillerde
     * (geri kazanılamaz) bozar; ay kümesi sabit ve iskeletleri tekil olduğu için bu
     * yöntem bilinmeyen bozulmalarda bile doğru sonucu verir. Bulunamazsa null.
     */
    static String monthCodeFromSkeleton(String raw) {
        if (raw == null) return null;
        String sk = raw.replaceAll("[^A-Za-z]", "").toLowerCase(java.util.Locale.ENGLISH);
        if (sk.isEmpty()) return null;
        String code = SKELETON_MONTH_MAP.get(sk);
        if (code != null) return code;
        if (sk.contains("ub")) return "02";                                            // Şubat
        if (sk.contains("au") || (sk.startsWith("a") && sk.endsWith("u"))) return "08"; // Ağustos
        return null;
    }

    /**
     * Sözleşme adındaki bozuk ay token'ını temiz Türkçe kısaltmayla değiştirir
     * (örn. {@code "USDTRY (26 �ub 27) Vadeli"} → {@code "USDTRY (26 Şub 27) Vadeli"}).
     * Akbank kaynağı Türkçe harfi kayıpla gönderdiği için görünüm adı buradan onarılır.
     */
    public static String canonicalizeContractName(String name) {
        if (name == null || name.isBlank()) return name;
        Matcher m = DATE_PAREN_PATTERN.matcher(name);
        if (!m.find()) return name;
        String code = monthCodeFromSkeleton(m.group(1));
        if (code == null) return name;
        String abbrev = CODE_TO_ABBREV.get(code);
        if (abbrev == null) return name;
        return name.substring(0, m.start(1)) + abbrev + name.substring(m.end(1));
    }

    /**
     * Akbank HTML encoding bozukluklarından kaynaklanan bozuk ay token'ları → ay kodu.
     * Her bozuk varyant buraya statik olarak eklenir.
     * HashMap kullanılıyor — Map.ofEntries duplicate key'e izin vermez.
     */
    private static final Map<String, String> BROKEN_MONTH_MAP;
    static {
        Map<String, String> m = new java.util.HashMap<>();
        // ===== Ağustos (08) bozuk varyantları =====
        m.put("AĞYu",           "08");  // ekranda görülen: Ğ+Y bozukluğu
        m.put("AĞY",            "08");
        m.put("ĞYu",            "08");
        m.put("Äžu",            "08");  // windows-1252: ğ → ž
        m.put("Äž",             "08");
        m.put("A\u00C4\u009Fu", "08");  // ISO-8859-1: C4 9F = ğ
        m.put("A\u00C4\u009F",  "08");
        m.put("Ä\u009Fu",       "08");
        m.put("Ä\u009F",        "08");
        m.put("AÄ\u009Eu",      "08");  // büyük Ğ varyantı
        m.put("AÄ\u009E",       "08");
        // ===== Şubat (02) bozuk varyantları =====
        m.put("Aşub",           "02");  // Ş → Aş bozukluğu
        m.put("Aşu",            "02");
        m.put("Åub",            "02");  // ekranda görülen: Å+ub
        m.put("Âub",            "02");  // UTF-8/Latin-1: Â+ub (Şubat)
        m.put("Å\u009Eub",      "02");  // ISO-8859-1: C5 9E = Ş
        m.put("Å\u009Eu",       "02");
        // ===== Ocak (01) bozuk varyantları =====
        m.put("Ä±ca",           "01");  // ı → Ä±
        // ===== Eylül (09) bozuk varyantları =====
        m.put("Eyl\u00FCl",     "09");  // ü → \u00FC
        BROKEN_MONTH_MAP = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * Akbank sözleşme adından İş Yatırım endeks kodunu üretir.
     *
     * @param contractName Akbank sözleşme adı (örn: "THYAO (30 Haz 26) Vadeli FIZ.")
     * @return İş Yatırım endeks kodu (örn: "F_THYAO0626"), desteklenmiyorsa empty
     */
    public Optional<String> toIsYatirimEndeksCode(String contractName) {
        if (contractName == null || contractName.isBlank()) {
            return Optional.empty();
        }

        String trimmed = contractName.trim();

        // Opsiyon sözleşmelerini reddet
        if (OPTION_PATTERN.matcher(trimmed).find()) {
            log.debug("Option contract not supported: '{}'", contractName);
            return Optional.empty();
        }

        // ELCBAS özel formatları — vade prefix'te encode edilmiş, generic mapper kullanılamaz.
        Optional<String> elcbas = tryElcbas(trimmed);
        if (elcbas.isPresent()) {
            log.debug("ELCBAS mapped '{}' -> '{}'", contractName, elcbas.get());
            return elcbas;
        }

        Matcher matcher = FUTURES_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            log.debug("Contract name does not match futures pattern: '{}'", contractName);
            return Optional.empty();
        }

        String underlying = aliasUnderlying(matcher.group(1).toUpperCase());
        String monthRaw   = matcher.group(3);
        String year       = matcher.group(4);

        String monthCode = resolveMonth(monthRaw);
        if (monthCode == null) {
            log.warn("Unknown month abbreviation: '{}' in contract: '{}'", monthRaw, contractName);
            return Optional.empty();
        }

        String endeksCode = "F_" + underlying + monthCode + year;
        log.debug("Mapped '{}' -> '{}'", contractName, endeksCode);
        return Optional.of(endeksCode);
    }

    /**
     * ELCBAS (elektrik) sözleşmeleri için Akbank → İş Yatırım kod çevirimi.
     * Vade Akbank sembol prefix'inde kodlanmış (örn. ELCBAS05 = aylık prefix, ELCBASQ3 = Q3,
     * ELCBASY = yıllık) ve parantezde sadece vade-sonu tarihi var.
     *
     * Backend resolver pattern'leri:
     *   F_ELCBASQ{quarter}{YY} → quarterly
     *   F_ELCBASY{YY}          → yearly
     *   F_ELCBAS{MM}{YY}       → monthly
     */
    private Optional<String> tryElcbas(String contractName) {
        Matcher q = ELCBAS_QUARTERLY_PATTERN.matcher(contractName);
        if (q.find()) {
            String quarter = q.group(1);
            String year    = q.group(2);
            return Optional.of("F_ELCBASQ" + quarter + year);
        }
        Matcher y = ELCBAS_YEARLY_PATTERN.matcher(contractName);
        if (y.find()) {
            return Optional.of("F_ELCBASY" + y.group(1));
        }
        Matcher m = ELCBAS_MONTHLY_PATTERN.matcher(contractName);
        if (m.find()) {
            // Sembol prefix'i ELCBAS05/06 vb. — vade ayını parantezdeki ay token'ından çöz
            // (Akbank gösterimde ay her zaman parantezdeki "DD Ay YY" alanında doğru).
            String monthCode = resolveMonth(m.group(1));
            if (monthCode == null) return Optional.empty();
            String year = m.group(2);
            return Optional.of("F_ELCBAS" + monthCode + year);
        }
        return Optional.empty();
    }

    public boolean isSupportedFuture(String contractName) {
        return toIsYatirimEndeksCode(contractName).isPresent();
    }

    /**
     * Bozuk encoding varyantlarını temiz Türkçe ay kısaltmasına normalize eder.
     * Akbank HTML'den gelen "Ağu" kelimesi encoding sorunları nedeniyle
     * farklı şekillerde bozulabilir.
     */
    private static String normalizeMonthRaw(String raw) {
        if (raw == null) return null;
        return raw
                // "Ağu" bozuk varyantları (Ağustos = Ağu)
                .replace("AĞYu", "Ağu")
                .replace("AĞY",  "Ağ")
                .replace("ĞYu",  "ğu")
                .replace("ĞY",   "ğ")
                .replace("Äžu",  "ğu")
                .replace("Äž",   "ğ")
                .replace("Ä\u009Fu", "ğu")
                .replace("Ä\u009F",  "ğ")
                .replace("A\u00C4\u009Fu", "Ağu")
                .replace("A\u00C4\u009F",  "Ağ")
                // "Aşub" → "Şub" (Şubat bozuk encode: Ş → Aş varyantı)
                .replace("Aşub", "Şub")
                .replace("Aşu",  "Şu")
                // "Åub" / "Âub" → "Şub" (Şubat bozuk encode)
                .replace("Åub",  "Şub")
                .replace("Åu",   "Şu")
                .replace("Âub",  "Şub")
                .replace("Âu",   "Şu")
                // "Şub" bozuk varyantları (Şubat = Şub)
                .replace("Å\u009Eub", "Şub")
                .replace("Å\u009Eu",  "Şu")
                // "ı" bozukluğu (Oca, Nis için)
                .replace("Ä±",  "ı")
                // "İ" bozukluğu
                .replace("Ä°",  "İ")
                // "ş" bozukluğu (Şub, Eyl için)
                .replace("Å\u009F", "ş")
                // "ö" bozukluğu (Eki için)
                .replace("Ã¶", "ö")
                // "ü" bozukluğu (Şub için)
                .replace("Ã¼", "ü");
    }

    /**
     * Ay kısaltmasını çözer.
     * Önce bilinen bozuk varyantlara bakar (BROKEN_MONTH_MAP),
     * sonra temiz Türkçe kısaltmalara (MONTH_MAP),
     * son olarak normalize ederek tekrar dener.
     */
    private String resolveMonth(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        // 1. Bilinen bozuk varyant — direkt eşleşme
        if (BROKEN_MONTH_MAP.containsKey(raw)) {
            log.debug("Resolved broken month token '{}' -> {}", raw, BROKEN_MONTH_MAP.get(raw));
            return BROKEN_MONTH_MAP.get(raw);
        }

        // 2. Temiz Türkçe kısaltma — direkt eşleşme
        if (MONTH_MAP.containsKey(raw)) return MONTH_MAP.get(raw);

        // 3. Normalize et (encoding düzelt), tekrar dene
        String normalized = normalizeMonthRaw(raw);

        if (BROKEN_MONTH_MAP.containsKey(normalized)) return BROKEN_MONTH_MAP.get(normalized);
        if (MONTH_MAP.containsKey(normalized)) return MONTH_MAP.get(normalized);

        // 4. Capitalize
        String cap = normalized.substring(0, 1).toUpperCase()
                + normalized.substring(1).toLowerCase();
        if (MONTH_MAP.containsKey(cap)) return MONTH_MAP.get(cap);

        // 5. Case-insensitive karşılaştırma
        String upper = normalized.toUpperCase();
        for (Map.Entry<String, String> e : MONTH_MAP.entrySet()) {
            if (e.getKey().toUpperCase().equals(upper)) return e.getValue();
        }

        // 6. Sağlam yedek: ASCII iskeleti (U+FFFD dahil her türlü bozulmayı çözer)
        String skeletonCode = monthCodeFromSkeleton(raw);
        if (skeletonCode != null) {
            log.debug("Resolved month via ASCII skeleton: raw='{}' -> {}", raw, skeletonCode);
            return skeletonCode;
        }

        log.warn("Could not resolve month from raw='{}', normalized='{}'", raw, normalized);
        return null;
    }
}