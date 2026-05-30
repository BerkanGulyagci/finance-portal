package com.finance.portal.market.infrastructure.external.indicator;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EconomicIndicatorClient {

    private static final Logger log = LoggerFactory.getLogger(EconomicIndicatorClient.class);

    // Veri kaynağı config'lenebilir (İster 2.1.3): env/yaml ile override edilebilir.
    // (Eski hardcoded TUIK_CPI_URL kaldırıldı — hiç kullanılmıyordu; TÜFE verisi zaten EVDS'ten gelir.)
    @Value("${market.indicators.tcmb-mpc-url:https://www.tcmb.gov.tr/wps/wcm/connect/EN/TCMB+EN/MPC/MPC+Meeting+Decisions}")
    private String tcmbMpcUrl;

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;

    public EconomicIndicatorClient(RestTemplate restTemplate,
                                   CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.integrationLog = integrationLog;
    }

    private void logIntegrationFailure(String eventType, String message, String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_TCMB,
                "fetchPolicyRate",
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                EconomicIndicatorClient.class.getName()
        );
    }

    /**
     * Fetches current TCMB policy rate from MPC decisions page.
     * Returns e.g. "37" or null on failure.
     */
    public String fetchPolicyRate() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                tcmbMpcUrl, HttpMethod.GET, entity, String.class);
            String httpStatus = String.valueOf(response.getStatusCode().value());
            String html = response.getBody();
            if (html == null || html.isBlank()) {
                log.warn("TCMB MPC page returned empty body");
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "TCMB policy rate: empty response body (page may have changed)",
                        httpStatus,
                        Map.of("url", tcmbMpcUrl));
                return null;
            }

            // Pattern: "policy rate ... at XX percent" or "from XX percent to XX percent"
            Pattern p1 = Pattern.compile("policy rate.*?at\\s+(\\d+(?:\\.\\d+)?)\\s+percent", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m1 = p1.matcher(html);
            if (m1.find()) return m1.group(1);

            Pattern p2 = Pattern.compile("to\\s+(\\d+(?:\\.\\d+)?)\\s+percent", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(html);
            if (m2.find()) return m2.group(1);

            // HTML geldi ama hiçbir desen eşleşmedi —
            // muhtemelen TCMB MPC sayfasının metni/yapısı değişti (sessiz hata).
            log.warn("TCMB policy rate not found in MPC page (no pattern matched)");
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "TCMB policy rate: no rate pattern matched in MPC page (page structure may have changed)",
                    httpStatus,
                    Map.of("url", tcmbMpcUrl, "htmlLength", html.length()));
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch TCMB policy rate: {}", e.getMessage());
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "TCMB policy rate fetch/parse failed: " + e.getMessage(),
                    null,
                    Map.of("url", tcmbMpcUrl, "exceptionClass", e.getClass().getSimpleName()));
            return null;
        }
    }
}
