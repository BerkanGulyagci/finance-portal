package com.finance.portal.market.infrastructure.external.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TradingView ekonomik takvim client'ı.
 * Finnhub'ın {@code /calendar/economic} endpoint'i ücretsiz planda kaldırıldığı için
 * onun yerine geçer. API anahtarı GEREKTİRMEZ.
 *
 * <p><b>Endpoint:</b> {@code GET https://economic-calendar.tradingview.com/events?from=ISO&to=ISO}
 * <br><b>Auth:</b> Yok — yalnız {@code Origin: https://www.tradingview.com} header'ı zorunludur
 * (header olmadan 403 döner; bu, widget'ın CORS doğrulamasıdır, kimlik doğrulama değil).
 *
 * <p><b>Yanıt:</b> {@code { "status": "ok", "result": [ {id, title, country, currency, importance,
 * actual, forecast, previous, unit, date, ...}, ... ] }}. 103 ülke kapsar (Türkiye dahil — TÜİK
 * kaynaklı TR olayları).
 *
 * <p><b>Alan eşlemesi</b> (TradingView → {@link EconomicCalendarEvent}):
 * <ul>
 *   <li>{@code date} (ISO 8601 UTC, örn. {@code 2026-06-15T07:00:00.000Z}) → {@code time}
 *       ({@code "yyyy-MM-dd HH:mm:ss"} UTC, modelin Finnhub formatı).</li>
 *   <li>{@code country} (ISO alpha-2, örn. {@code TR}) → {@code country}.</li>
 *   <li>{@code currency} (örn. {@code TRY}) → {@code currency} (TradingView doğrudan verir —
 *       Finnhub'da ülkeden türetiliyordu).</li>
 *   <li>{@code title} → {@code event}.</li>
 *   <li>{@code importance} ({@code 1}=high, {@code 0}=medium, {@code -1}=low) →
 *       {@code impact} ({@code "high"|"medium"|"low"}).</li>
 *   <li>{@code actual}/{@code forecast}/{@code previous} → {@code actual}/{@code estimate}/{@code prev}.</li>
 *   <li>{@code unit} (örn. {@code %}) → {@code unit}.</li>
 * </ul>
 *
 * <p>Hata/boş/erişim yok durumunda boş liste döner — exception fırlatılmaz (Port sözleşmesi).
 * Dayanıklılık {@code EconomicCalendarService} üzerinden LKG cache ile sağlanır: kaynak çökerse
 * son başarılı veri servis edilir.
 */
@Component
public class TradingViewCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(TradingViewCalendarClient.class);

    /** TradingView ISO 8601 (örn. 2026-06-15T07:00:00.000Z) — request from/to için. */
    private static final DateTimeFormatter REQ_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /** Model {@code time} alanı formatı — Finnhub konvansiyonuyla aynı ("yyyy-MM-dd HH:mm:ss" UTC). */
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Tarayıcı benzeri User-Agent — ZORUNLU. TradingView'in nginx'i UA'sız/bot-benzeri istekleri
     * 403 ile reddeder (varsayılan Java/RestClient UA bloklanır). Bu, Origin header'ına ek bir
     * tarayıcı-doğrulama katmanıdır (kimlik doğrulama değil).
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final RestClient restClient;
    private final CentralIntegrationLogService integrationLog;
    private final String baseUrl;
    private final String originHeader;

    // NOT: {@code java.net.HttpURLConnection} ({@code SimpleClientHttpRequestFactory}'nin altı)
    // {@code Origin} gibi "kısıtlı" header'ları SESSİZCE düşürür → TradingView nginx 403 döner
    // (Origin zorunlu). Çözüm {@code -Dsun.net.http.allowRestrictedHeaders=true} JVM argümanıdır
    // (Dockerfile ENTRYPOINT'inde set edilir). Runtime {@code System.setProperty} GEÇ KALIR —
    // restricted header listesi sınıf yükleme anında bir kez initialize olur. Bu yüzden burada
    // değil, JVM başlangıcında verilir.

    public TradingViewCalendarClient(
            CentralIntegrationLogService integrationLog,
            @Value("${tradingview.calendar.url:https://economic-calendar.tradingview.com/events}") String baseUrl,
            @Value("${tradingview.calendar.origin:https://www.tradingview.com}") String originHeader
    ) {
        // SimpleClientHttpRequestFactory (HttpURLConnection) — JDK HttpClient DEĞİL. JDK HttpClient
        // Origin'i hiç göndertmez (allowRestrictedHeaders ona etki etmez); HttpURLConnection +
        // yukarıdaki sistem özelliği ile Origin geçer. (CoinGeckoClient ile aynı factory.)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        this.integrationLog = integrationLog;
        this.baseUrl = baseUrl;
        this.originHeader = originHeader;
    }

    /** TradingView her zaman erişilebilir (API anahtarı yok) — Finnhub'ın isEnabled muadili. */
    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public List<EconomicCalendarEvent> fetch(LocalDate from, LocalDate to) {
        if (!isEnabled()) {
            log.warn("[TradingViewCalendar] base-url yapılandırılmamış");
            return List.of();
        }
        // Aralığı gün başına genişlet: from günün başı, to günün SONU (kapsayıcı).
        String fromIso = from.atStartOfDay().atOffset(ZoneOffset.UTC).format(REQ_FMT);
        String toIso = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).format(REQ_FMT);

        String requestUrl = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("from", fromIso)
                .queryParam("to", toIso)
                .build(true).toUriString();

        long started = System.currentTimeMillis();
        try {
            JsonNode root = restClient.get()
                    .uri(requestUrl)
                    // Origin + User-Agent ZORUNLU — ikisi de yoksa nginx 403 döner (tarayıcı
                    // doğrulaması, kimlik doğrulama değil). Java/RestClient varsayılan UA'sı bloklanır.
                    .header(HttpHeaders.ORIGIN, originHeader)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) {
                logIntegrationFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "TradingView takvim yanıtı null", "calendar.fetch", null,
                        Map.of("from", from.toString(), "to", to.toString()));
                return List.of();
            }
            JsonNode arr = root.path("result");
            if (!arr.isArray() || arr.isEmpty()) {
                return List.of();
            }
            List<EconomicCalendarEvent> out = new ArrayList<>(arr.size());
            for (JsonNode n : arr) {
                EconomicCalendarEvent ev = parseOne(n);
                if (ev != null) out.add(ev);
            }
            log.debug("[TradingViewCalendar] {} olay ({} → {}) — {} ms",
                    out.size(), from, to, System.currentTimeMillis() - started);
            return out;
        } catch (HttpClientErrorException e) {
            log.warn("[TradingViewCalendar] HTTP {} from={} to={} — {}",
                    e.getStatusCode(), from, to, e.getMessage());
            logIntegrationFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                    "TradingView takvim HTTP hatası: " + e.getStatusCode(), "calendar.fetch",
                    String.valueOf(e.getStatusCode().value()),
                    Map.of("from", from.toString(), "to", to.toString()));
            return List.of();
        } catch (ResourceAccessException e) {
            log.error("[TradingViewCalendar] Bağlantı hatası from={} to={}: {}", from, to, e.getMessage());
            logIntegrationFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                    "TradingView takvim bağlantı hatası", "calendar.fetch", null,
                    Map.of("from", from.toString(), "to", to.toString(), "error", String.valueOf(e.getMessage())));
            return List.of();
        } catch (Exception e) {
            log.error("[TradingViewCalendar] Beklenmeyen hata from={} to={}: {}", from, to, e.getMessage());
            logIntegrationFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "TradingView takvim parse hatası", "calendar.fetch", null,
                    Map.of("from", from.toString(), "to", to.toString(), "error", String.valueOf(e.getMessage())));
            return List.of();
        }
    }

    private EconomicCalendarEvent parseOne(JsonNode n) {
        try {
            EconomicCalendarEvent ev = new EconomicCalendarEvent();
            ev.setTime(toModelTime(text(n, "date")));
            ev.setCountry(text(n, "country"));
            ev.setCurrency(text(n, "currency"));
            ev.setEvent(text(n, "title"));
            ev.setImpact(mapImpact(n.get("importance")));
            ev.setActual(num(n, "actual"));
            ev.setEstimate(num(n, "forecast"));
            ev.setPrev(num(n, "previous"));
            ev.setUnit(text(n, "unit"));
            // Olayın ne event metni ne de zamanı yoksa atla (Finnhub ile aynı kural).
            if ((ev.getEvent() == null || ev.getEvent().isBlank())
                    && (ev.getTime() == null || ev.getTime().isBlank())) {
                return null;
            }
            return ev;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ISO 8601 UTC ({@code 2026-06-15T07:00:00.000Z}) → model {@code "yyyy-MM-dd HH:mm:ss"} (UTC).
     * Parse edilemezse ham değeri döndürür (en azından kaybolmaz).
     */
    private static String toModelTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .format(TIME_FMT);
        } catch (Exception e) {
            return iso;
        }
    }

    /** TradingView importance ({@code 1|0|-1}) → impact ({@code "high"|"medium"|"low"}). */
    private static String mapImpact(JsonNode v) {
        if (v == null || v.isNull() || !v.isNumber()) return null;
        int imp = v.asInt();
        if (imp >= 1) return "high";
        if (imp == 0) return "medium";
        return "low"; // -1 ve altı
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText(null);
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Double num(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || !v.isNumber()) return null;
        return v.asDouble();
    }

    private void logIntegrationFailure(String eventType, String message, String operation,
                                       String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                "tradingview",
                operation,
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                TradingViewCalendarClient.class.getName()
        );
    }
}
