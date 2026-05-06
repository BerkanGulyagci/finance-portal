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
            "^([A-ZÇĞİÖŞÜ0-9]{4,7})\\s*\\((\\d{2})\\s+(\\S+)\\s+(\\d{2})\\).*Vadeli",
            Pattern.UNICODE_CHARACTER_CLASS
    );

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

        Matcher matcher = FUTURES_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            log.debug("Contract name does not match futures pattern: '{}'", contractName);
            return Optional.empty();
        }

        String underlying = matcher.group(1).toUpperCase();
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
                // "Åub" → "Şub" (ekranda görülen: Å+ub varyantı)
                .replace("Åub",  "Şub")
                .replace("Åu",   "Şu")
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

        log.warn("Could not resolve month from raw='{}', normalized='{}'", raw, normalized);
        return null;
    }
}