package com.finance.portal.market.infrastructure.external.indicator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EconomicIndicatorClient {

    private static final Logger log = LoggerFactory.getLogger(EconomicIndicatorClient.class);

    private static final String TCMB_MPC_URL =
        "https://www.tcmb.gov.tr/wps/wcm/connect/EN/TCMB+EN/MPC/MPC+Meeting+Decisions";
    private static final String TUIK_CPI_URL =
        "https://data.tuik.gov.tr/Bulten/Index?p=Tuketici-Fiyat-Endeksi-Mart-2026-53826";

    private final RestTemplate restTemplate;

    public EconomicIndicatorClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
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
                TCMB_MPC_URL, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            if (html == null) return null;

            // Pattern: "policy rate ... at XX percent" or "from XX percent to XX percent"
            Pattern p1 = Pattern.compile("policy rate.*?at\\s+(\\d+(?:\\.\\d+)?)\\s+percent", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m1 = p1.matcher(html);
            if (m1.find()) return m1.group(1);

            Pattern p2 = Pattern.compile("to\\s+(\\d+(?:\\.\\d+)?)\\s+percent", Pattern.CASE_INSENSITIVE);
            Matcher m2 = p2.matcher(html);
            if (m2.find()) return m2.group(1);

            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch TCMB policy rate: {}", e.getMessage());
            return null;
        }
    }
}
