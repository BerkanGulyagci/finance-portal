package com.finance.portal.market.infrastructure.external.tefas;

import com.finance.portal.market.application.funds.model.TefasFundHistoryPoint;
import com.finance.portal.market.application.funds.model.TefasFundItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class TefasFundClient {

    private static final Logger log = LoggerFactory.getLogger(TefasFundClient.class);

    private static final String BASE_URL = "https://www.tefas.gov.tr";
    private static final String INFO_ENDPOINT = BASE_URL + "/api/DB/BindHistoryInfo";
    private static final DateTimeFormatter TEFAS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RestTemplate restTemplate;

    public TefasFundClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches all funds for the last business day from TEFAS.
     * @param kind YAT (mutual funds), EMK (pension), BYF (ETF)
     */
    public List<TefasFundItem> fetchFunds(String kind) {
        String date = getLastBusinessDay();
        log.debug("Fetching TEFAS funds for date: {}", date);
        return fetchFunds(kind, date, date, "");
    }

    public List<TefasFundItem> fetchFundByCode(String code) {
        String date = getLastBusinessDay();
        // Tüm fon tiplerini dene, hangisi veri döndürürse onu kullan
        for (String kind : new String[]{"YAT", "BYF", "EMK", "GYF", "GSYF"}) {
            List<TefasFundItem> result = fetchFunds(kind, date, date, code.toUpperCase());
            if (!result.isEmpty()) return result;
        }
        return List.of();
    }

    /**
     * Tarihsel fiyat verisi — aylık parçalar halinde çeker (WAF bypass).
     * Her ay ayrı istek, paralel çekilir, sonuçlar birleştirilir.
     */
    private static final ExecutorService HISTORY_EXECUTOR = Executors.newFixedThreadPool(6);

    public List<TefasFundHistoryPoint> fetchHistory(String code, LocalDate from, LocalDate to) {
        // Aralık uzunluğuna göre chunk boyutunu belirle
        long totalMonths = java.time.temporal.ChronoUnit.MONTHS.between(from, to) + 1;
        int chunkMonths = totalMonths > 24 ? 3 : 1; // 2 yıldan uzunsa 3'er aylık parçalar

        List<LocalDate[]> chunks = new ArrayList<>();
        LocalDate chunkStart = from;
        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = chunkStart.plusMonths(chunkMonths).minusDays(1);
            if (chunkEnd.isAfter(to)) chunkEnd = to;
            chunks.add(new LocalDate[]{chunkStart, chunkEnd});
            chunkStart = chunkEnd.plusDays(1);
        }

        List<CompletableFuture<List<TefasFundHistoryPoint>>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(
                        () -> fetchHistoryChunk(code, chunk[0], chunk[1]), HISTORY_EXECUTOR))
                .toList();

        List<TefasFundHistoryPoint> all = new ArrayList<>();
        for (CompletableFuture<List<TefasFundHistoryPoint>> f : futures) {
            try {
                all.addAll(f.get(20, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("Failed to fetch TEFAS history chunk for {}: {}", code, e.getMessage());
            }
        }
        all.sort(Comparator.comparing(TefasFundHistoryPoint::getDate));
        return all;
    }

    @SuppressWarnings("unchecked")
    private List<TefasFundHistoryPoint> fetchHistoryChunk(String code, LocalDate from, LocalDate to) {
        // Doğru fon tipini bul
        String kind = detectFundKind(code, to);

        HttpHeaders headers = buildHeaders();
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("fontip", kind);
        body.add("bastarih", from.format(TEFAS_DATE_FORMAT));
        body.add("bittarih", to.format(TEFAS_DATE_FORMAT));
        body.add("fonkod", code.toUpperCase());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(INFO_ENDPOINT, request, Map.class);
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) return List.of();
            Object data = responseBody.get("data");
            if (!(data instanceof List)) return List.of();

            List<TefasFundHistoryPoint> result = new ArrayList<>();
            for (Object item : (List<?>) data) {
                if (item instanceof Map) {
                    TefasFundHistoryPoint pt = mapToHistoryPoint((Map<?, ?>) item);
                    if (pt != null) result.add(pt);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("TEFAS history chunk failed {}/{}: {}", code, from, e.getMessage());
            return List.of();
        }
    }

    private TefasFundHistoryPoint mapToHistoryPoint(Map<?, ?> map) {
        try {
            String tarih = str(map, "TARIH");
            if (tarih == null) return null;
            // TARIH epoch ms → yyyy-MM-dd
            String date = java.time.Instant.ofEpochMilli(Long.parseLong(tarih))
                    .atZone(java.time.ZoneId.of("Europe/Istanbul"))
                    .toLocalDate()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            TefasFundHistoryPoint pt = new TefasFundHistoryPoint();
            pt.setDate(date);
            pt.setPrice(parseBigDecimal(str(map, "FIYAT")));
            pt.setNumberOfInvestors(parseInvestorCount(str(map, "KISISAYISI")));
            pt.setMarketCap(parseBigDecimal(str(map, "PORTFOYBUYUKLUK")));
            pt.setSharesInCirculation(parseBigDecimal(str(map, "TEDPAYSAYISI")));
            return pt;
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns last Friday if today is weekend, otherwise today */
    private String getLastBusinessDay() {
        LocalDate date = LocalDate.now();
        // If Saturday → go back 1 day to Friday
        // If Sunday → go back 2 days to Friday
        if (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
            date = date.minusDays(1);
        } else if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            date = date.minusDays(2);
        }
        return date.format(TEFAS_DATE_FORMAT);
    }

    @SuppressWarnings("unchecked")
    private List<TefasFundItem> fetchFunds(String kind, String startDate, String endDate, String fundCode) {
        HttpHeaders headers = buildHeaders();

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("fontip", kind);
        body.add("bastarih", startDate);
        body.add("bittarih", endDate);
        body.add("fonkod", fundCode);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(INFO_ENDPOINT, request, Map.class);
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) return List.of();

            Object data = responseBody.get("data");
            if (!(data instanceof List)) return List.of();

            List<TefasFundItem> result = new ArrayList<>();
            for (Object item : (List<?>) data) {
                if (item instanceof Map) {
                    TefasFundItem fund = mapToFundItem((Map<?, ?>) item);
                    if (fund != null) result.add(fund);
                }
            }
            log.debug("Fetched {} TEFAS funds (kind={})", result.size(), kind);
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch TEFAS funds: {}", e.getMessage());
            return List.of();
        }
    }

    private TefasFundItem mapToFundItem(Map<?, ?> map) {
        try {
            TefasFundItem item = new TefasFundItem();
            item.setCode(str(map, "FONKODU"));
            item.setTitle(str(map, "FONUNVAN"));
            item.setPrice(parseBigDecimal(str(map, "FIYAT")));
            item.setDailyReturnPercent(parseBigDecimal(str(map, "GUNLUKGETIRI")));
            item.setMarketCap(parseBigDecimal(str(map, "PORTFOYBUYUKLUK")));
            item.setNumberOfInvestors(parseInvestorCount(str(map, "KISISAYISI")));
            item.setSharesInCirculation(parseBigDecimal(str(map, "TEDPAYSAYISI")));
            item.setBorsaBultenFiyat(parseBigDecimal(str(map, "BORSABULTENFIYAT")));
            item.setDate(str(map, "TARIH"));
            return item;
        } catch (Exception e) {
            log.debug("Failed to map TEFAS fund item: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Public wrapper — fon kodunun tipini döndürür (YAT, BYF, EMK, GYF, GSYF).
     */
    public String detectKind(String code) {
        return detectFundKind(code, LocalDate.now());
    }

    // Fon kodu → fon tipi cache (uygulama ömrü boyunca)
    private final java.util.concurrent.ConcurrentHashMap<String, String> fundKindCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Fon kodunun hangi fon tipine ait olduğunu tespit eder.
     * Sonucu in-memory cache'e alır.
     */
    private String detectFundKind(String code, LocalDate referenceDate) {
        return fundKindCache.computeIfAbsent(code.toUpperCase(), k -> {
            String date = referenceDate.format(TEFAS_DATE_FORMAT);
            for (String kind : new String[]{"YAT", "BYF", "EMK", "GYF", "GSYF"}) {
                List<TefasFundItem> result = fetchFunds(kind, date, date, k);
                if (!result.isEmpty()) {
                    log.debug("Fund {} detected as kind={}", k, kind);
                    return kind;
                }
            }
            log.warn("Could not detect fund kind for {}, defaulting to YAT", k);
            return "YAT";
        });
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.set("Origin", BASE_URL);
        headers.set("Referer", BASE_URL + "/TarihselVeriler.aspx");
        return headers;
    }

    private String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private java.math.BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new java.math.BigDecimal(value.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.replace(".", "").replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * TEFAS KISISAYISI alanı "769.0" gibi double formatında geliyor.
     * Önce double parse edip sonra long'a çevir.
     */
    private Long parseInvestorCount(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            // "769.0" → 769
            return (long) Double.parseDouble(value);
        } catch (Exception e) {
            try {
                return Long.parseLong(value.replace(".", "").replace(",", ""));
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
