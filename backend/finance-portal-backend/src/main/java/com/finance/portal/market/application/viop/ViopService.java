package com.finance.portal.market.application.viop;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.market.application.viop.port.ViopContractListPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ViopService {

    private static final Logger log = LoggerFactory.getLogger(ViopService.class);

    /**
     * Akbank kontrat adı: {@code THYAO (25 May 26) Vadeli FIZ.}
     */
    private static final Pattern CONTRACT_NAME_PATTERN = Pattern.compile(
            "^(\\S+)\\s+\\(\\s*(\\d+)\\s+(\\S+)\\s+(\\d{2,4})\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // TradingView sembol mapping (sadece bilinen olanlar)
    private static final Map<String, String> TRADINGVIEW_SYMBOL_MAP = new HashMap<>();

    static {
        // Endeks vadeli işlemleri
        TRADINGVIEW_SYMBOL_MAP.put("XU030", "BIST:XU030");
        TRADINGVIEW_SYMBOL_MAP.put("XU100", "BIST:XU100");
        TRADINGVIEW_SYMBOL_MAP.put("XUSIN", "BIST:XUSIN");
        TRADINGVIEW_SYMBOL_MAP.put("XBANK", "BIST:XBANK");
        
        // Döviz vadeli işlemleri
        TRADINGVIEW_SYMBOL_MAP.put("USDTRY", "FX:USDTRY");
        TRADINGVIEW_SYMBOL_MAP.put("EURTRY", "FX:EURTRY");
        
        // Not: Hisse senedi vadeli işlemleri için TradingView'da genelde sembol yok
        // Sadece bilinen ve doğrulanmış sembolleri ekleyin
    }

    private final ViopContractListPort viopContractListPort;
    private final ViopService self;

    public ViopService(ViopContractListPort viopContractListPort, @Lazy ViopService self) {
        this.viopContractListPort = viopContractListPort;
        this.self = self;
    }

    @Cacheable(cacheNames = "market.viop.contracts", key = "'all'")
    public List<ViopContract> getAllContracts() {
        List<ViopContract> list = viopContractListPort.fetchContracts();
        return list != null ? list : List.of();
    }

    /**
     * Belirli bir hisse senedine ait VİOP kontratlarını döndürür
     * Örnek: "ALARK" için "ALARK (..." ile başlayan tüm kontratlar
     * Kontrat formatı: "SASA (25 May 26) Vadeli FIZ."
     */
    public List<ViopContract> getContractsByUnderlyingAsset(String underlyingSymbol) {
        List<ViopContract> allContracts = getAllContracts();
        
        log.info("Searching VIOP contracts for underlying asset: {}", underlyingSymbol);
        log.info("Total contracts available: {}", allContracts.size());
        
        // İlk 5 kontratın ismini logla
        allContracts.stream()
                .limit(5)
                .forEach(c -> log.info("Sample contract name: {}", c.getName()));
        
        // Kontrat formatı: "SYMBOL (tarih) Vadeli..." şeklinde
        // Örnek: "ALARK (25 May 26) Vadeli FIZ."
        String searchPattern = underlyingSymbol.toUpperCase(Locale.ROOT) + " (";

        List<ViopContract> filtered = allContracts.stream()
                .filter(contract -> contract.getName() != null
                        && contract.getName().toUpperCase(Locale.ROOT).startsWith(searchPattern))
                .toList();
        
        log.info("Found {} contracts for {} (searching for pattern: '{}')", 
                filtered.size(), underlyingSymbol, searchPattern);
        
        return filtered;
    }

    /**
     * Kontrat adı normalizasyonu (lookup / saklama).
     * trim, ardışık boşlukları tek boşluğa indir, sondaki noktayı kaldırı (Akbank: {@code "... FIZ."}).
     */
    public static String normalizeContractNameInput(String name) {
        if (name == null) {
            return "";
        }
        String t = name.trim().replaceAll("\\s+", " ");
        while (t.endsWith(".")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    /**
     * İşlem kaydında saklanacak VIOP görünen adı: {@link Locale#ROOT} ile büyük harf (Türkçe locale tuzaklarından kaçınır).
     */
    public static String normalizeStoredFutureSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        String n = normalizeContractNameInput(symbol);
        return n.isEmpty() ? null : n.toUpperCase(Locale.ROOT);
    }

    /**
     * Akbank listesinde portföy / URL'den gelen adla eşleşen ham kontrat (önbellekteki liste).
     */
    public Optional<ViopContract> findMatchingContract(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        List<ViopContract> contracts = getAllContracts();
        if (contracts.isEmpty()) {
            return Optional.empty();
        }
        String needle = normalizeContractLookupKey(name);
        if (needle.isEmpty()) {
            return Optional.empty();
        }
        return findContractForPortfolioSymbol(needle, contracts);
    }

    /**
     * Ham {@link ViopContract} → detay DTO (portföy enrichment; listeden doğrudan).
     */
    public ViopContractDetail buildDetailDto(ViopContract contract) {
        if (contract == null) {
            throw new IllegalArgumentException("contract must not be null");
        }
        String shortSymbol = extractSymbol(contract.getName());
        return mapToDetailDto(contract, shortSymbol);
    }

    /**
     * HTTP / istemci: kontrat detayı. Beklenmeyen hataları yutup 404 benzeri mesaj (500 önleme).
     * Önbellek: {@link #getContractDetailCached} (proxy üzerinden).
     */
    public ViopContractDetail getContractDetail(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new IllegalArgumentException("VIOP kontrat adı boş olamaz");
        }
        String trimmed = symbol.trim();
        try {
            return self.getContractDetailCached(trimmed);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("VIOP getContractDetail unexpected error for '{}'", trimmed, ex);
            throw new ResourceNotFoundException(
                    "VIOP kontrat detayı alınamadı. Listeyi yenileyip kontratı tekrar seçin.");
        }
    }

    /**
     * Önbellekli detay. SpEL'de {@code #p0} — parametre adına bağımlı değil.
     */
    @Cacheable(
            cacheNames = "market.viop.detail",
            key = "T(org.springframework.util.DigestUtils).md5DigestAsHex(#p0.getBytes(T(java.nio.charset.StandardCharsets).UTF_8))"
    )
    public ViopContractDetail getContractDetailCached(String symbol) {
        List<ViopContract> contracts = getAllContracts();

        if (contracts.isEmpty()) {
            log.error("No VIOP contracts available from Akbank");
            throw new IllegalArgumentException("VIOP kontratları yüklenemedi. Lütfen daha sonra tekrar deneyin.");
        }

        log.info("Searching for VIOP contract: '{}'", symbol);
        log.info("Total contracts available: {}", contracts.size());

        final String needle = normalizeContractLookupKey(symbol);
        if (needle.isEmpty()) {
            throw new IllegalArgumentException("VIOP kontrat adı boş olamaz");
        }

        ViopContract contract = findContractForPortfolioSymbol(needle, contracts)
                .orElseThrow(() -> {
                    log.warn("VIOP contract not found (normalized): '{}'", needle);
                    log.warn("Sample available contracts: {}",
                            contracts.stream().map(ViopContract::getName).limit(5).toList());
                    return new ResourceNotFoundException("VIOP kontratı bulunamadı: " + symbol);
                });

        log.info("Contract found: '{}'", contract.getName());

        String shortSymbol = extractSymbol(contract.getName());
        return mapToDetailDto(contract, shortSymbol);
    }

    private static String normalizeContractLookupKey(String name) {
        return normalizeContractNameInput(name);
    }

    /**
     * Akbank ile portföy arasında büyük/küçük harf, Türkçe İ/I/ı/i ve yaygın yazım (HIZ/FIZ) farklarını giderir.
     * DB'de tümü büyük harf saklı kayıtlar (örn. "VADELI") ile Akbank listesindeki "Vadeli"
     * Türkçe locale lowercase'inde {@code vadelı} vs {@code vadeli} olarak farklı düşer; o yüzden önce
     * Türkçe karakterleri ASCII'ye fold ediyor sonra {@link Locale#ROOT} ile küçültüyoruz.
     */
    private static String viopComparableKey(String name) {
        String t = normalizeContractLookupKey(name);
        if (t.isEmpty()) {
            return "";
        }
        // Fiziki teslim: bazen "HIZ" / "HIZ." olarak kayıt düşüyor
        t = t.replaceAll("(?i)\\bHIZ\\b", "FIZ");
        t = asciiFoldTr(t);
        return t.toLowerCase(Locale.ROOT);
    }

    /**
     * Portföy işlemlerinde (BUY/SELL, bakiye) aynı VİOP kontratını tek kimlik altında görmek için.
     * {@link #normalizeStoredFutureSymbol} yerine tolerant eşitleme (İ/I/ı, Vadeli/VADELİ, son nokta, vb.).
     */
    public static String portfolioFutureGroupKey(String raw) {
        return viopComparableKey(raw != null ? raw : "");
    }

    /** Türkçe özel harfleri ASCII karşılığına indirir (eşleştirme için). */
    private static String asciiFoldTr(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'İ' -> sb.append('I');
                case 'ı' -> sb.append('i');
                case 'Ş' -> sb.append('S');
                case 'ş' -> sb.append('s');
                case 'Ğ' -> sb.append('G');
                case 'ğ' -> sb.append('g');
                case 'Ü' -> sb.append('U');
                case 'ü' -> sb.append('u');
                case 'Ö' -> sb.append('O');
                case 'ö' -> sb.append('o');
                case 'Ç' -> sb.append('C');
                case 'ç' -> sb.append('c');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private Optional<ViopContract> findContractForPortfolioSymbol(String needle, List<ViopContract> contracts) {
        final String key = viopComparableKey(needle);
        Optional<ViopContract> exact = contracts.stream()
                .filter(c -> c.getName() != null && viopComparableKey(c.getName()).equals(key))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }

        ViopNameParts requested = parseViopNameParts(needle);
        if (requested == null) {
            return Optional.empty();
        }

        List<ViopContract> sameSlot = contracts.stream()
                .filter(c -> c.getName() != null)
                .map(c -> new ParsedContract(c, parseViopNameParts(normalizeContractLookupKey(c.getName()))))
                .filter(pc -> pc.parts != null)
                .filter(pc -> requested.underlying.equals(pc.parts.underlying)
                        && requested.dayOfMonth == pc.parts.dayOfMonth
                        && requested.month == pc.parts.month)
                .map(pc -> pc.contract)
                .toList();

        if (sameSlot.isEmpty()) {
            return Optional.empty();
        }
        if (sameSlot.size() == 1) {
            log.info("VIOP matched by underlying+day+month: requested '{}' -> '{}'",
                    needle, sameSlot.get(0).getName());
            return Optional.of(sameSlot.get(0));
        }

        // Aynı gün/ay için birden fazla yıl (ör. süresi dolmuş kayıt vs listedeki güncel kontrat)
        List<ViopContract> ranked = sameSlot.stream()
                .sorted(Comparator.comparingInt(c -> {
                    ViopNameParts p = parseViopNameParts(normalizeContractLookupKey(c.getName()));
                    return p != null ? p.year : 0;
                }))
                .toList();

        int uy = requested.year;
        Optional<ViopContract> bestGe = ranked.stream()
                .filter(c -> {
                    ViopNameParts p = parseViopNameParts(normalizeContractLookupKey(c.getName()));
                    return p != null && p.year >= uy;
                })
                .findFirst();
        ViopContract chosen = bestGe.orElse(ranked.get(ranked.size() - 1));
        ViopNameParts chosenParts = parseViopNameParts(normalizeContractLookupKey(chosen.getName()));
        log.info("VIOP fuzzy match (same dayanak+gün+ay, picked year={}): requested '{}' -> '{}'",
                chosenParts != null ? chosenParts.year : "?",
                needle, chosen.getName());
        return Optional.of(chosen);
    }

    private record ViopNameParts(String underlying, int dayOfMonth, int month, int year) {}

    private record ParsedContract(ViopContract contract, ViopNameParts parts) {}

    private static ViopNameParts parseViopNameParts(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return null;
        }
        Matcher m = CONTRACT_NAME_PATTERN.matcher(normalizedName);
        if (!m.find()) {
            return null;
        }
        String underlying = m.group(1).trim().toUpperCase(Locale.ROOT);
        int day = Integer.parseInt(m.group(2));
        Integer month = monthFromToken(m.group(3));
        if (month == null || day < 1 || day > 31) {
            return null;
        }
        int y = expandYear(Integer.parseInt(m.group(4)));
        return new ViopNameParts(underlying, day, month, y);
    }

    private static int expandYear(int yy) {
        if (yy >= 100) {
            return yy;
        }
        return yy >= 70 ? 1900 + yy : 2000 + yy;
    }

    /** Akbank / İngilizce / Türkçe ay kısaltmaları — ASCII fold + ROOT locale, ilk 3 harf. */
    private static Integer monthFromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String w = asciiFoldTr(raw.trim()).toLowerCase(Locale.ROOT);
        if (w.length() >= 3) {
            w = w.substring(0, 3);
        }
        return switch (w) {
            case "oca" -> 1;
            case "sub" -> 2;
            case "mar" -> 3;
            case "nis" -> 4;
            case "may" -> 5;
            case "haz" -> 6;
            case "tem" -> 7;
            case "agu" -> 8;
            case "eyl" -> 9;
            case "eki" -> 10;
            case "kas" -> 11;
            case "ara" -> 12;
            case "jan" -> 1;
            case "feb" -> 2;
            case "apr" -> 4;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dec" -> 12;
            default -> null;
        };
    }

    private ViopContractDetail mapToDetailDto(ViopContract contract, String symbol) {
        ViopContractDetail dto = new ViopContractDetail();
        dto.setName(contract.getName());
        dto.setSymbol(symbol);
        dto.setLastPrice(parseBigDecimal(contract.getLastPrice()));
        dto.setChangePercent(parsePercent(contract.getChangePercent()));
        dto.setHigh(parseBigDecimal(contract.getHigh()));
        dto.setLow(parseBigDecimal(contract.getLow()));
        dto.setOpenPositionCount(parseLong(contract.getOpenPositionCount()));
        dto.setOpenPositionChange(parseLong(contract.getOpenPositionChange()));
        dto.setSettlementPrice(parseBigDecimal(contract.getSettlementPrice()));
        dto.setPrevSettlementPrice(parseBigDecimal(contract.getPrevSettlementPrice()));
        dto.setTime(contract.getTime());
        
        // TradingView sembolü varsa ekle (yoksa null kalır)
        String symKey = symbol != null ? symbol : "";
        String tvSymbol = TRADINGVIEW_SYMBOL_MAP.get(symKey.toUpperCase(Locale.ROOT));
        dto.setTradingViewSymbol(tvSymbol);
        
        return dto;
    }

    /**
     * Kontrat adından sembol çıkarır.
     * Örnek: "AEFES Vadeli 25 May 26" → "AEFES"
     */
    private String extractSymbol(String name) {
        if (name == null) return "";
        Matcher vm = Pattern.compile("(?iu)vadeli").matcher(name);
        if (vm.find()) {
            return name.substring(0, vm.start()).trim();
        }
        // Boşluktan önceki ilk kelimeyi al
        int idx = name.indexOf(" ");
        if (idx > 0) {
            return name.substring(0, idx).trim();
        }
        return name.trim();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            // Türkçe format: 1.234,56 → 1234.56
            String normalized = value.replace(".", "").replace(",", ".");
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal: {}", value);
            return null;
        }
    }

    private BigDecimal parsePercent(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            // "%+1,23" → 1.23
            String normalized = value.replace("%", "").replace("+", "")
                    .replace(".", "").replace(",", ".");
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse percent: {}", value);
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            // "1.234" → 1234
            String normalized = value.replace(".", "").replace(",", "");
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse Long: {}", value);
            return null;
        }
    }
}
