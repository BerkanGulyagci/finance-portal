package com.finance.portal.market.infrastructure.external.tefas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TEFAS FonAnaliz.aspx sayfasından dönem getirilerini HTML scraping ile çeker.
 * Getiri bilgileri statik HTML'de <span style="font-size: 24px;">%X,XXXXXX</span> formatında.
 */
@Component
public class TefasFundAnalysisScraper {

    private static final Logger log = LoggerFactory.getLogger(TefasFundAnalysisScraper.class);
    private static final String BASE_URL = "https://www.tefas.gov.tr/FonAnaliz.aspx?FonKod=";

    // Getiri değerleri: %4,659826 formatında
    private static final Pattern RETURN_PATTERN =
            Pattern.compile("<span style=\"font-size: 24px;\">%([\\d,.-]+)</span>");

    // Günlük getiri: farklı bir yerde
    private static final Pattern DAILY_RETURN_PATTERN =
            Pattern.compile("G[üu]nl[üu]k Getiri.*?<span[^>]*>%?([\\d,.-]+)</span>",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    // Risk değeri: 1-7 arası tam sayı — tablo satırında "Fonun Risk Değeri" etiketi yanında
    // HTML yapısı: <td>Fonun Risk Değeri</td><td>6</td>
    private static final Pattern RISK_PATTERN =
            Pattern.compile("Fonun Risk De[gğ]eri.{0,300}?>\\s*([1-7])\\s*<",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;

    public TefasFundAnalysisScraper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fon analiz sayfasından dönem getirilerini çeker.
     * @return map: return1M, return3M, return6M, return1Y, dailyReturn
     */
    public Map<String, Double> fetchReturns(String code) {
        Map<String, Double> result = new LinkedHashMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0");
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + code.toUpperCase(), HttpMethod.GET, entity, String.class);

            String html = response.getBody();
            if (html == null || html.isBlank()) return result;

            // 4 dönem getirisi: 1A, 3A, 6A, 1Y sırasıyla
            Matcher m = RETURN_PATTERN.matcher(html);
            String[] keys = {"return1M", "return3M", "return6M", "return1Y"};
            int idx = 0;
            while (m.find() && idx < keys.length) {
                String raw = m.group(1).replace(",", ".");
                try {
                    result.put(keys[idx], Double.parseDouble(raw));
                    idx++;
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse return value: {}", raw);
                }
            }

            // Günlük getiri — BindHistoryInfo'dan zaten geliyor ama burada da çekebiliriz
            Matcher dm = DAILY_RETURN_PATTERN.matcher(html);
            if (dm.find()) {
                try {
                    result.put("dailyReturn", Double.parseDouble(dm.group(1).replace(",", ".")));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse daily return");
                }
            }

            // Risk değeri (1-7)
            Matcher rm = RISK_PATTERN.matcher(html);
            if (rm.find()) {
                try {
                    result.put("riskValue", Double.parseDouble(rm.group(1)));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse risk value");
                }
            }

            log.debug("Scraped returns for {}: {}", code, result);
        } catch (Exception e) {
            log.warn("Failed to scrape TEFAS returns for {}: {}", code, e.getMessage());
        }
        return result;
    }
}
