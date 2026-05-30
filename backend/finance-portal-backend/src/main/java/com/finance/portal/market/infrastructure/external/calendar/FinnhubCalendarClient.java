package com.finance.portal.market.infrastructure.external.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.calendar.model.EconomicCalendarEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finnhub {@code /calendar/economic} client.
 * Hata/boş/anahtar yok durumunda boş liste döner — exception fırlatılmaz.
 *
 * <p>API: {@code GET https://finnhub.io/api/v1/calendar/economic?from=YYYY-MM-DD&to=YYYY-MM-DD&token=...}
 * <br>Yanıt: {@code { "economicCalendar": [ {time, country, event, impact, actual, estimate, prev, unit}, ... ] }}
 */
@Component
public class FinnhubCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubCalendarClient.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** ISO 3166-1 alpha-2 → ISO 4217 currency code (yaygın ülkeler). */
    private static final Map<String, String> COUNTRY_TO_CURRENCY = new HashMap<>();
    static {
        COUNTRY_TO_CURRENCY.put("US", "USD"); COUNTRY_TO_CURRENCY.put("EU", "EUR");
        COUNTRY_TO_CURRENCY.put("GB", "GBP"); COUNTRY_TO_CURRENCY.put("JP", "JPY");
        COUNTRY_TO_CURRENCY.put("CH", "CHF"); COUNTRY_TO_CURRENCY.put("CA", "CAD");
        COUNTRY_TO_CURRENCY.put("AU", "AUD"); COUNTRY_TO_CURRENCY.put("NZ", "NZD");
        COUNTRY_TO_CURRENCY.put("TR", "TRY"); COUNTRY_TO_CURRENCY.put("CN", "CNY");
        COUNTRY_TO_CURRENCY.put("IN", "INR"); COUNTRY_TO_CURRENCY.put("BR", "BRL");
        COUNTRY_TO_CURRENCY.put("RU", "RUB"); COUNTRY_TO_CURRENCY.put("MX", "MXN");
        COUNTRY_TO_CURRENCY.put("KR", "KRW"); COUNTRY_TO_CURRENCY.put("SG", "SGD");
        COUNTRY_TO_CURRENCY.put("HK", "HKD"); COUNTRY_TO_CURRENCY.put("ZA", "ZAR");
        COUNTRY_TO_CURRENCY.put("NO", "NOK"); COUNTRY_TO_CURRENCY.put("SE", "SEK");
        COUNTRY_TO_CURRENCY.put("DK", "DKK"); COUNTRY_TO_CURRENCY.put("PL", "PLN");
        COUNTRY_TO_CURRENCY.put("CZ", "CZK"); COUNTRY_TO_CURRENCY.put("HU", "HUF");
        COUNTRY_TO_CURRENCY.put("IL", "ILS"); COUNTRY_TO_CURRENCY.put("AE", "AED");
        COUNTRY_TO_CURRENCY.put("SA", "SAR"); COUNTRY_TO_CURRENCY.put("TH", "THB");
        COUNTRY_TO_CURRENCY.put("ID", "IDR"); COUNTRY_TO_CURRENCY.put("MY", "MYR");
        COUNTRY_TO_CURRENCY.put("PH", "PHP"); COUNTRY_TO_CURRENCY.put("TW", "TWD");
        COUNTRY_TO_CURRENCY.put("CL", "CLP"); COUNTRY_TO_CURRENCY.put("CO", "COP");
        COUNTRY_TO_CURRENCY.put("AR", "ARS"); COUNTRY_TO_CURRENCY.put("PE", "PEN");
        COUNTRY_TO_CURRENCY.put("EG", "EGP"); COUNTRY_TO_CURRENCY.put("NG", "NGN");
        COUNTRY_TO_CURRENCY.put("PK", "PKR"); COUNTRY_TO_CURRENCY.put("VN", "VND");
        // Eurozone üyeleri — para birimi EUR
        COUNTRY_TO_CURRENCY.put("DE", "EUR"); COUNTRY_TO_CURRENCY.put("FR", "EUR");
        COUNTRY_TO_CURRENCY.put("IT", "EUR"); COUNTRY_TO_CURRENCY.put("ES", "EUR");
        COUNTRY_TO_CURRENCY.put("NL", "EUR"); COUNTRY_TO_CURRENCY.put("BE", "EUR");
        COUNTRY_TO_CURRENCY.put("AT", "EUR"); COUNTRY_TO_CURRENCY.put("PT", "EUR");
        COUNTRY_TO_CURRENCY.put("IE", "EUR"); COUNTRY_TO_CURRENCY.put("FI", "EUR");
        COUNTRY_TO_CURRENCY.put("GR", "EUR");
    }

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;
    private final String url;
    private final String apiKey;

    public FinnhubCalendarClient(
            RestTemplate restTemplate,
            CentralIntegrationLogService integrationLog,
            @Value("${finnhub.calendar.url:https://finnhub.io/api/v1/calendar/economic}") String url,
            @Value("${news.sources.finnhub.key:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.integrationLog = integrationLog;
        this.url = url;
        this.apiKey = apiKey;
    }

    /** API anahtarı yapılandırılmış mı? */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<EconomicCalendarEvent> fetch(LocalDate from, LocalDate to) {
        if (!isEnabled()) {
            log.warn("[FinnhubCalendar] API anahtarı yapılandırılmamış");
            return List.of();
        }
        String requestUrl = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("from", from.format(DATE_FMT))
                .queryParam("to", to.format(DATE_FMT))
                .queryParam("token", apiKey)
                .build(true).toUriString();

        long started = System.currentTimeMillis();
        try {
            JsonNode root = restTemplate.getForObject(requestUrl, JsonNode.class);
            if (root == null) {
                logIntegrationFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "Finnhub takvim yanıtı null", "calendar.fetch", null,
                        Map.of("from", from.toString(), "to", to.toString()));
                return List.of();
            }
            JsonNode arr = root.path("economicCalendar");
            if (!arr.isArray() || arr.size() == 0) {
                return List.of();
            }
            List<EconomicCalendarEvent> out = new ArrayList<>(arr.size());
            for (JsonNode n : arr) {
                EconomicCalendarEvent ev = parseOne(n);
                if (ev != null) out.add(ev);
            }
            log.debug("[FinnhubCalendar] {} olay ({} → {}) — {} ms",
                    out.size(), from, to, System.currentTimeMillis() - started);
            return out;
        } catch (HttpClientErrorException e) {
            log.warn("[FinnhubCalendar] HTTP {} from={} to={} — {}",
                    e.getStatusCode(), from, to, e.getMessage());
            logIntegrationFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                    "Finnhub takvim HTTP hatası: " + e.getStatusCode(), "calendar.fetch",
                    String.valueOf(e.getRawStatusCode()),
                    Map.of("from", from.toString(), "to", to.toString()));
            return List.of();
        } catch (ResourceAccessException e) {
            log.error("[FinnhubCalendar] Bağlantı hatası from={} to={}: {}", from, to, e.getMessage());
            logIntegrationFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                    "Finnhub takvim bağlantı hatası", "calendar.fetch", null,
                    Map.of("from", from.toString(), "to", to.toString(), "error", String.valueOf(e.getMessage())));
            return List.of();
        } catch (Exception e) {
            log.error("[FinnhubCalendar] Beklenmeyen hata from={} to={}: {}", from, to, e.getMessage());
            logIntegrationFailure(IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "Finnhub takvim parse hatası", "calendar.fetch", null,
                    Map.of("from", from.toString(), "to", to.toString(), "error", String.valueOf(e.getMessage())));
            return List.of();
        }
    }

    private EconomicCalendarEvent parseOne(JsonNode n) {
        try {
            EconomicCalendarEvent ev = new EconomicCalendarEvent();
            ev.setTime(text(n, "time"));
            String country = text(n, "country");
            ev.setCountry(country);
            ev.setCurrency(country != null ? COUNTRY_TO_CURRENCY.get(country.toUpperCase()) : null);
            ev.setEvent(text(n, "event"));
            ev.setImpact(text(n, "impact"));
            ev.setActual(num(n, "actual"));
            ev.setEstimate(num(n, "estimate"));
            ev.setPrev(num(n, "prev"));
            ev.setUnit(text(n, "unit"));
            // Olayın ne event metni ne de zamanı yoksa atla
            if ((ev.getEvent() == null || ev.getEvent().isBlank()) && (ev.getTime() == null || ev.getTime().isBlank())) {
                return null;
            }
            return ev;
        } catch (Exception e) {
            return null;
        }
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
                "finnhub",
                operation,
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                FinnhubCalendarClient.class.getName()
        );
    }
}
