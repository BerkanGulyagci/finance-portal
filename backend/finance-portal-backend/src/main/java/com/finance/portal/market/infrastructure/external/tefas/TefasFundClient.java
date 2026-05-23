package com.finance.portal.market.infrastructure.external.tefas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.market.application.funds.model.FundPriceHistoryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TEFAS resmi fon API client — fon birim pay fiyatı tarihsel serisi.
 *
 * POST https://www.tefas.gov.tr/api/funds/fonGnlBlgSiraliGetir
 * Body (JSON): {"fonTipi":"YAT","fonKodu":"AFA","basTarih":"YYYYMMDD","bitTarih":"YYYYMMDD",...}
 * Yanıt: {"resultList":[{"fonKodu","fonUnvan","tarih","fiyat","portfoyBuyukluk",...}]}
 *
 * <p><b>Neden curl?</b> TEFAS önünde F5/Shape bot-koruması var ve <i>JVM'in JSSE TLS parmak izini</i>
 * (JA3) reddedip {@code 500 {"error":"Proxy request failed"}} döndürüyor — RestTemplate /
 * {@code java.net.http.HttpClient} dahil tüm JDK HTTP istemcileri aynı JSSE parmak izini kullandığı için
 * engelleniyor. Aynı container'dan {@code curl} (OpenSSL) sorunsuz veri çekiyor. Bu yüzden istek
 * curl alt-süreci ile yapılır (gövde stdin'den geçer; argümanlar shell'siz ProcessBuilder ile verilir,
 * komut enjeksiyonu yok). Sonuç servis katmanında cache'lendiği için curl nadiren çalışır.
 *
 * <p>Kısıt: TEFAS tek istekte ~30 günle sınırlı (31 gün boş döner) → aralık {@code chunkDays} (28)
 * günlük parçalara bölünüp paralel çekilir, gün bazında birleştirilir.
 */
@Component
public class TefasFundClient {

    private static final Logger log = LoggerFactory.getLogger(TefasFundClient.class);
    private static final DateTimeFormatter REQ_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper;
    private final TefasProperties props;
    private final ExecutorService executor;

    public TefasFundClient(ObjectMapper objectMapper, TefasProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
        this.executor = Executors.newFixedThreadPool(Math.max(1, props.getParallelism()), daemonFactory());
    }

    private static ThreadFactory daemonFactory() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "tefas-history-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * Bir fonun {@code [from, to]} aralığındaki günlük birim pay fiyatlarını çeker.
     * Parçalara bölüp paralel çeker; gün bazında tekilleştirip artan sıraya koyar.
     *
     * @param fonTipi YAT (yatırım fonu / TEFAS) | EMK (emeklilik). Boşsa YAT.
     * @return Tarih artan sıralı liste; veri yoksa boş liste.
     */
    public List<FundPriceHistoryPoint> fetchPriceHistory(String code, String fonTipi, LocalDate from, LocalDate to) {
        if (code == null || code.isBlank() || from == null || to == null || from.isAfter(to)) {
            return List.of();
        }
        String upper = code.trim().toUpperCase();
        String type = (fonTipi == null || fonTipi.isBlank()) ? "YAT" : fonTipi.trim().toUpperCase();
        int chunk = Math.max(1, props.getChunkDays());

        List<LocalDate[]> windows = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate end = cursor.plusDays(chunk - 1L);
            if (end.isAfter(to)) end = to;
            windows.add(new LocalDate[]{cursor, end});
            cursor = end.plusDays(1);
        }

        List<CompletableFuture<List<FundPriceHistoryPoint>>> futures = new ArrayList<>();
        for (LocalDate[] w : windows) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchChunk(upper, type, w[0], w[1]), executor));
        }

        NavigableMap<String, FundPriceHistoryPoint> byDay = new TreeMap<>();
        for (CompletableFuture<List<FundPriceHistoryPoint>> f : futures) {
            try {
                for (FundPriceHistoryPoint p : f.get(props.getTimeoutSeconds() + 10L, TimeUnit.SECONDS)) {
                    if (p.getDate() != null && p.getPrice() != null) {
                        byDay.putIfAbsent(p.getDate(), p);
                    }
                }
            } catch (Exception e) {
                log.debug("TEFAS history chunk failed for {} ({}): {}", upper, type, e.getMessage());
            }
        }

        log.info("TEFAS history: code={}, type={}, {} nokta ({} chunk, {}..{})",
                upper, type, byDay.size(), windows.size(), from, to);
        return new ArrayList<>(byDay.values());
    }

    /**
     * Tek parça çeker. TEFAS, aynı IP'den yoğun paralel isteklerde throttle edip {@code resultList}
     * içermeyen hata gövdesi (ör. {@code {"error":"Proxy request failed"}}) döndürebildiği için
     * <b>resultList içermeyen</b> yanıtlarda kısa backoff ile yeniden dener. {@code resultList} bir dizi
     * (boş bile olsa) ise o gün için gerçekten veri yok demektir → yeniden denemez.
     */
    private List<FundPriceHistoryPoint> fetchChunk(String code, String fonTipi, LocalDate from, LocalDate to) {
        String body;
        try {
            body = objectMapper.writeValueAsString(buildBody(code, fonTipi, from, to));
        } catch (Exception e) {
            return List.of();
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String response = curlPost(props.getHistoryUrl(), body);
                if (response != null && !response.isBlank()) {
                    JsonNode list = objectMapper.readTree(response).path("resultList");
                    if (list.isArray()) {
                        List<FundPriceHistoryPoint> out = new ArrayList<>(list.size());
                        for (JsonNode row : list) {
                            String date = text(row, "tarih");
                            BigDecimal price = decimal(row, "fiyat");
                            if (date == null || price == null || price.signum() <= 0) {
                                continue;
                            }
                            String day = date.length() >= 10 ? date.substring(0, 10) : date;
                            out.add(new FundPriceHistoryPoint(day, price, decimal(row, "portfoyBuyukluk")));
                        }
                        return out; // resultList var → kesin sonuç (boş bile olsa)
                    }
                }
            } catch (Exception e) {
                log.debug("TEFAS chunk error {} {}..{} (deneme {}): {}", code, from, to, attempt, e.getMessage());
            }
            // resultList yok (throttle/hata) → kısa backoff ile tekrar dene
            try { Thread.sleep(250L * attempt); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return List.of();
    }

    /**
     * curl alt-süreci ile POST. Argümanlar shell'siz (ProcessBuilder) verilir; gövde stdin'den geçer
     * (komut enjeksiyonu yok). TEFAS, JVM JSSE TLS parmak izini engellediği için curl kullanılır.
     */
    private String curlPost(String url, String jsonBody) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                "curl", "-s", "--max-time", String.valueOf(props.getTimeoutSeconds()),
                "-X", "POST", url,
                "-H", "Content-Type: application/json",
                "-H", "Accept: application/json",
                "-H", "User-Agent: " + UA,
                "-H", "Origin: https://www.tefas.gov.tr",
                "-H", "Referer: https://www.tefas.gov.tr/tr/fon-verileri"));
        String token = props.getBearerToken();
        if (token != null && !token.isBlank()) {
            cmd.add("-H");
            cmd.add("Authorization: Bearer " + token);
        }
        cmd.add("--data-binary");
        cmd.add("@-"); // gövde stdin'den

        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        try (OutputStream os = p.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        byte[] stdout = p.getInputStream().readAllBytes();
        boolean done = p.waitFor(props.getTimeoutSeconds() + 5L, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            return null;
        }
        if (p.exitValue() != 0) {
            log.debug("curl exit {} for TEFAS POST", p.exitValue());
            return null;
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    private Map<String, Object> buildBody(String code, String fonTipi, LocalDate from, LocalDate to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fonTipi", fonTipi);
        body.put("fonKodu", code);
        body.put("basTarih", from.format(REQ_FMT));
        body.put("bitTarih", to.format(REQ_FMT));
        body.put("basSira", 1);
        body.put("bitSira", 100000);
        body.put("dil", "TR");
        body.put("sFonTurKod", "");
        body.put("fonKod", "");
        body.put("fonGrup", "");
        body.put("fonUnvanTip", "");
        return body;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText(null);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String raw = node.get(field).asText(null);
        if (raw == null || raw.isBlank() || "NaN".equalsIgnoreCase(raw)) return null;
        try { return new BigDecimal(raw); } catch (Exception e) { return null; }
    }
}
