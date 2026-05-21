package com.finance.portal.market.infrastructure.external.gold;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * canlialtinfiyatlari.com'dan altin fiyatlarini HTML scraping ile ceker.
 * Sayfa statik HTML dondurur, JavaScript gerektirmez.
 *
 * Donen map key'leri:
 *   ALTIN_ONS, GRAM_ALTIN, HAS_ALTIN,
 *   CEYREK_ALTIN, YARIM_ALTIN, TAM_ALTIN,
 *   ATA_ALTIN, BESLI_ATA,
 *   CEYREK_ESKI, YARIM_ESKI, TAM_ESKI,
 *   AYAR_14, AYAR_22
 */
@Component
public class GoldScraper {

    private static final Logger log = LoggerFactory.getLogger(GoldScraper.class);
    private static final String SCRAPE_URL = "https://canlialtinfiyatlari.com/";

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;

    public GoldScraper(RestTemplate restTemplate,
                       CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.integrationLog = integrationLog;
    }

    private void logScrapeFailure(String eventType, String message, String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_CANLI_ALTIN,
                "scrapeGoldPrices",
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                GoldScraper.class.getName()
        );
    }

    public Map<String, GoldPriceEntry> fetchAll() {
        Map<String, GoldPriceEntry> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0");
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9,en;q=0.8");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    SCRAPE_URL, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            String httpStatus = String.valueOf(response.getStatusCode().value());
            if (html == null || html.isBlank()) {
                log.warn("canlialtinfiyatlari.com returned empty response");
                logScrapeFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "canlialtinfiyatlari.com gold scrape: empty response body",
                        httpStatus,
                        Map.of("url", SCRAPE_URL));
                return result;
            }

            parseTable(html, result);

            // Sayfa geldi ama 0 satir cikarildi — HTML yapisi degismis olabilir (sessiz hata).
            if (result.isEmpty()) {
                log.warn("canlialtinfiyatlari.com returned HTML but 0 gold prices were extracted");
                logScrapeFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "canlialtinfiyatlari.com gold scrape: 0 rows extracted - HTML structure may have changed",
                        httpStatus,
                        Map.of("url", SCRAPE_URL, "htmlLength", html.length()));
                return result;
            }

            log.info("Fetched {} gold prices from canlialtinfiyatlari.com", result.size());
        } catch (Exception e) {
            log.error("Failed to fetch gold prices from canlialtinfiyatlari.com: {}", e.getMessage());
            logScrapeFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "canlialtinfiyatlari.com gold scrape: fetch/parse failed (HTML structure may have changed): " + e.getMessage(),
                    null,
                    Map.of("url", SCRAPE_URL, "exceptionClass", e.getClass().getSimpleName()));
        }
        return result;
    }

    /**
     * HTML'deki ikinci tabloyu parse eder.
     * Her satir blogu:
     *   ISIM
     *   HH:MM:SS
     *   ALIS_FIYATI
     *   SATIS_FIYATI
     *   DEGISIM%
     */
    private void parseTable(String html, Map<String, GoldPriceEntry> result) {
        // Aranacak isimler ve normalize key'leri
        String[][] targets = {
            {"ALTIN ONS",     "ALTIN_ONS"},
            {"Has Alt",       "HAS_ALTIN"},
            {"GRAM ALTIN",    "GRAM_ALTIN"},
            {"14-AYAR",       "AYAR_14"},
            {"22-AYAR",       "AYAR_22"},
            {"Çeyrek Altın",  "CEYREK_ALTIN"},
            {"Yarım Altın",   "YARIM_ALTIN"},
            {"Tam Altın",     "TAM_ALTIN"},
            {"Ata Alt",       "ATA_ALTIN"},
            {"Beşli Ata",     "BESLI_ATA"},
            {"Çeyrek (Eski)", "CEYREK_ESKI"},
            {"Yarım (Eski)",  "YARIM_ESKI"},
            {"Tam (Eski)",    "TAM_ESKI"},
        };

        for (String[] target : targets) {
            String searchName = target[0];
            String key = target[1];

            int idx = html.indexOf(searchName);
            if (idx < 0) continue;

            // Sonraki ~300 karakteri al
            String chunk = html.substring(idx, Math.min(idx + 300, html.length()));

            // Saat: HH:MM:SS
            String time = null;
            Matcher timeMatcher = Pattern.compile("(\\d{2}:\\d{2}:\\d{2})").matcher(chunk);
            if (timeMatcher.find()) time = timeMatcher.group(1);

            // Fiyatlar: 3+ haneli sayisal degerler
            Pattern numPat = Pattern.compile("(\\d{3,6}(?:[.,]\\d+)?)");
            Matcher nm = numPat.matcher(chunk);
            BigDecimal buy = null, sell = null;
            int count = 0;
            while (nm.find() && count < 2) {
                String raw = nm.group(1);
                BigDecimal v = parsePrice(raw);
                if (v != null && v.compareTo(BigDecimal.valueOf(100)) > 0) {
                    if (count == 0) buy = v;
                    else sell = v;
                    count++;
                }
            }

            // Degisim yuzdesi
            BigDecimal pct = null;
            Matcher pctMatcher = Pattern.compile("([\\-+]?\\d+[.,]\\d+)%").matcher(chunk);
            if (pctMatcher.find()) pct = parsePercent(pctMatcher.group(1));

            if (buy != null && sell != null) {
                result.put(key, new GoldPriceEntry(searchName, buy, sell, pct, time));
            }
        }
    }

    private BigDecimal parsePrice(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String clean = s.trim();
            // Turkce format: nokta binlik ayrac, virgul ondalik
            // Ornek: "6.834,50" -> "6834.50"
            if (clean.contains(".") && clean.contains(",")) {
                clean = clean.replace(".", "").replace(",", ".");
            } else if (clean.contains(",")) {
                clean = clean.replace(",", ".");
            }
            BigDecimal v = new BigDecimal(clean);
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parsePercent(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim().replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }
}
