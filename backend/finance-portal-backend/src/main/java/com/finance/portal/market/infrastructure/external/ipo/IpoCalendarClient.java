package com.finance.portal.market.infrastructure.external.ipo;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.ipo.IpoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IpoCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(IpoCalendarClient.class);
    private static final String URL = "https://halkarz.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;

    public IpoCalendarClient(RestTemplate restTemplate,
                             CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.integrationLog = integrationLog;
    }

    private void logScrapeFailure(String eventType, String message, String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_HALKARZ,
                "scrapeIpoCalendar",
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                IpoCalendarClient.class.getName()
        );
    }

    public List<IpoItem> fetchIpos() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            String httpStatus = String.valueOf(response.getStatusCode().value());
            if (html == null || html.isBlank()) {
                log.warn("halkarz.com returned empty response");
                logScrapeFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "halkarz.com IPO calendar: empty response body",
                        httpStatus,
                        Map.of("url", URL));
                return List.of();
            }

            return parseHtml(html, httpStatus);
        } catch (Exception e) {
            log.warn("Failed to fetch IPO calendar: {}", e.getMessage());
            logScrapeFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "halkarz.com IPO calendar: fetch/parse failed (HTML structure may have changed): " + e.getMessage(),
                    null,
                    Map.of("url", URL, "exceptionClass", e.getClass().getSimpleName()));
            return List.of();
        }
    }

    private List<IpoItem> parseHtml(String html, String httpStatus) {
        List<IpoItem> result = new ArrayList<>();

        // Actual halkarz.com HTML structure:
        // <span class="il-bist-kod">TICKER</span>
        // <h3 class="il-halka-arz-sirket"><a href="URL">Company Name</a></h3>
        // <span class="il-halka-arz-tarihi"><time ...>Date</time></span>
        Pattern p = Pattern.compile(
                "<span class=\"il-bist-kod\">\\s*([A-Z0-9]{3,6})\\s*</span>\\s*" +
                "<h3[^>]*><a href=\"([^\"]+)\"[^>]*>([^<]+)</a></h3>\\s*" +
                "<span[^>]*>\\s*<time[^>]*>([^<]+)</time>",
                Pattern.DOTALL
        );

        Matcher m = p.matcher(html);
        while (m.find() && result.size() < 20) {
            IpoItem item = new IpoItem();
            item.setTicker(m.group(1).trim());
            item.setUrl(m.group(2).trim());
            item.setName(m.group(3).trim());
            item.setDate(m.group(4).trim());
            result.add(item);
        }

        // Sayfa geldi ama regex hicbir IPO satiri eslestiremedi —
        // HTML/selector yapisi degismis olabilir (sessiz hata).
        if (result.isEmpty()) {
            log.warn("halkarz.com returned HTML but 0 IPO items were extracted");
            logScrapeFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                    "halkarz.com IPO calendar: 0 rows extracted - HTML structure may have changed",
                    httpStatus,
                    Map.of("url", URL, "htmlLength", html.length()));
            return result;
        }

        log.debug("Parsed {} IPO items from halkarz.com", result.size());
        return result;
    }
}
