package com.finance.portal.market.infrastructure.external.tefas;

import com.finance.portal.market.application.funds.model.TefasFundHistoryPoint;
import com.finance.portal.market.application.funds.model.TefasFundItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * TEFAS yeni API (2026): https://www.tefas.gov.tr/api/funds/
 * JSON body, camelCase field isimleri.
 */
@Component
public class TefasFundClient {

    private static final Logger log = LoggerFactory.getLogger(TefasFundClient.class);

    private static final String BASE_URL      = "https://www.tefas.gov.tr";
    private static final String LIST_ENDPOINT = BASE_URL + "/api/funds/fonGnlBlgSiraliGetir";
    private static final DateTimeFormatter TEFAS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ISO_DATE   = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";

    private final RestTemplate restTemplate;

    public TefasFundClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ── Fon listesi ──────────────────────────────────────────────────────────

    public List<TefasFundItem> fetchFunds(String kind) {
        for (int i = 0; i <= 5; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            if (isWeekend(date)) continue;
            List<TefasFundItem> result = fetchFundList(kind, date, date, "");
            if (!result.isEmpty()) {
                log.debug("TEFAS funds fetched date={} kind={} count={}", date, kind, result.size());
                return result;
            }
        }
        log.warn("No TEFAS data for kind={}", kind);
        return List.of();
    }

    public List<TefasFundItem> fetchFundByCode(String code) {
        for (int i = 0; i <= 5; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            if (isWeekend(date)) continue;
            for (String kind : new String[]{"YAT", "BYF", "EMK", "GYF", "GSYF"}) {
                List<TefasFundItem> result = fetchFundList(kind, date, date, code.toUpperCase());
                if (!result.isEmpty()) {
                    result.get(0).setKind(kind);
                    return result;
                }
            }
        }
        return List.of();
    }

    public String detectKind(String code) {
        return fundKindCache.computeIfAbsent(code.toUpperCase(), k -> {
            for (int i = 0; i <= 5; i++) {
                LocalDate date = LocalDate.now().minusDays(i);
                if (isWeekend(date)) continue;
                for (String kind : new String[]{"YAT", "BYF", "EMK", "GYF", "GSYF"}) {
                    if (!fetchFundList(kind, date, date, k).isEmpty()) return kind;
                }
            }
            return "YAT";
        });
    }

    private final ConcurrentHashMap<String, String> fundKindCache = new ConcurrentHashMap<>();

    // ── Tarihsel veri ────────────────────────────────────────────────────────

    private static final ExecutorService HISTORY_EXECUTOR = Executors.newFixedThreadPool(4);

    public List<TefasFundHistoryPoint> fetchHistory(String code, LocalDate from, LocalDate to) {
        long totalMonths = java.time.temporal.ChronoUnit.MONTHS.between(from, to) + 1;
        int chunkMonths = totalMonths > 24 ? 3 : 1;

        List<LocalDate[]> chunks = new ArrayList<>();
        LocalDate cs = from;
        while (!cs.isAfter(to)) {
            LocalDate ce = cs.plusMonths(chunkMonths).minusDays(1);
            if (ce.isAfter(to)) ce = to;
            chunks.add(new LocalDate[]{cs, ce});
            cs = ce.plusDays(1);
        }

        String kind = detectKind(code);
        List<CompletableFuture<List<TefasFundHistoryPoint>>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(
                        () -> fetchHistoryChunk(code, kind, chunk[0], chunk[1]), HISTORY_EXECUTOR))
                .toList();

        List<TefasFundHistoryPoint> all = new ArrayList<>();
        for (var f : futures) {
            try { all.addAll(f.get(20, TimeUnit.SECONDS)); }
            catch (Exception e) { log.warn("History chunk failed {}: {}", code, e.getMessage()); }
        }
        all.sort(Comparator.comparing(TefasFundHistoryPoint::getDate));
        return all;
    }

    @SuppressWarnings("unchecked")
    private List<TefasFundHistoryPoint> fetchHistoryChunk(String code, String kind, LocalDate from, LocalDate to) {
        Map<String, Object> body = buildBody(kind, from, to, code.toUpperCase(), 1, 10000);
        try {
            var response = restTemplate.postForEntity(LIST_ENDPOINT,
                    new HttpEntity<>(body, jsonHeaders()), Map.class);
            var rb = response.getBody();
            if (rb == null) return List.of();
            Object list = rb.get("resultList");
            if (!(list instanceof List)) return List.of();
            List<TefasFundHistoryPoint> result = new ArrayList<>();
            for (Object item : (List<?>) list) {
                if (item instanceof Map) {
                    TefasFundHistoryPoint pt = mapToHistoryPoint((Map<?, ?>) item);
                    if (pt != null) result.add(pt);
                }
            }
            return result;
        } catch (Exception e) {
            log.debug("History chunk failed {}/{}: {}", code, from, e.getMessage());
            return List.of();
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<TefasFundItem> fetchFundList(String kind, LocalDate from, LocalDate to, String code) {
        Map<String, Object> body = buildBody(kind, from, to, code.isBlank() ? null : code, 1, 500);
        try {
            var response = restTemplate.postForEntity(LIST_ENDPOINT,
                    new HttpEntity<>(body, jsonHeaders()), Map.class);
            var rb = response.getBody();
            if (rb == null) return List.of();
            Object list = rb.get("resultList");
            if (!(list instanceof List)) return List.of();
            List<TefasFundItem> result = new ArrayList<>();
            for (Object item : (List<?>) list) {
                if (item instanceof Map) {
                    TefasFundItem fund = mapToFundItem((Map<?, ?>) item);
                    if (fund != null) result.add(fund);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch TEFAS fund list (kind={} date={}): {}", kind, from, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> buildBody(String kind, LocalDate from, LocalDate to,
                                           Object fonKodu, int basSira, int bitSira) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fonTipi", kind);
        body.put("basTarih", from.format(TEFAS_DATE));
        body.put("bitTarih", to.format(TEFAS_DATE));
        body.put("fonKodu", fonKodu);
        body.put("aramaMetni", null);
        body.put("fonTurKod", null);
        body.put("fonGrubu", null);
        body.put("sfonTurKod", null);
        body.put("kurucuKod", null);
        body.put("fonTurAciklama", null);
        body.put("basSira", basSira);
        body.put("bitSira", bitSira);
        body.put("dil", "TR");
        return body;
    }

    private TefasFundItem mapToFundItem(Map<?, ?> map) {
        try {
            TefasFundItem item = new TefasFundItem();
            item.setCode(str(map, "fonKodu"));
            item.setTitle(str(map, "fonUnvan"));
            item.setPrice(parseBD(map.get("fiyat")));
            item.setMarketCap(parseBD(map.get("portfoyBuyukluk")));
            item.setNumberOfInvestors(parseLong(map.get("kisiSayisi")));
            item.setSharesInCirculation(parseBD(map.get("tedPaySayisi")));
            item.setBorsaBultenFiyat(parseBD(map.get("borsaBultenFiyat")));
            item.setDate(str(map, "tarih"));
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    private TefasFundHistoryPoint mapToHistoryPoint(Map<?, ?> map) {
        try {
            TefasFundHistoryPoint pt = new TefasFundHistoryPoint();
            pt.setDate(str(map, "tarih"));
            pt.setPrice(parseBD(map.get("fiyat")));
            pt.setNumberOfInvestors(parseLong(map.get("kisiSayisi")));
            pt.setMarketCap(parseBD(map.get("portfoyBuyukluk")));
            pt.setSharesInCirculation(parseBD(map.get("tedPaySayisi")));
            return pt;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Accept", "application/json, text/plain, */*");
        h.set("Accept-Language", "tr-TR,tr;q=0.9");
        h.set("Origin", BASE_URL);
        h.set("Referer", BASE_URL + "/");
        h.set("User-Agent", UA);
        h.set("sec-fetch-site", "same-origin");
        h.set("sec-fetch-mode", "cors");
        return h;
    }

    private boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
            || d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
    }

    private String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private BigDecimal parseBD(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(value.toString()); }
        catch (Exception e) { return null; }
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        try { return (long) Double.parseDouble(value.toString()); }
        catch (Exception e) { return null; }
    }
}
