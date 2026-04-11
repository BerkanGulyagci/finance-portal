package com.finance.portal.market.infrastructure.external.ipo;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IpoCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(IpoCalendarClient.class);
    private static final String URL = "https://halkarz.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    // Matches: <a href="/company-slug/">SYMBOL\n### [Company Name]\n\nDate
    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "<a[^>]+href=\"(https://halkarz\\.com/[^\"]+)\"[^>]*>([A-Z0-9]+)\\s*</a>\\s*" +
            "<h3[^>]*>\\s*<a[^>]*>([^<]+)</a>\\s*</h3>\\s*<p[^>]*>([^<]+)</p>",
            Pattern.DOTALL
    );

    // Simpler fallback: extract name + ticker + date from rendered text blocks
    private static final Pattern BLOCK_PATTERN = Pattern.compile(
            "\\[([^\\]]+)\\]\\(https://halkarz\\.com/[^)]+\\)([A-Z0-9]{3,6})\\s*###\\s*\\[([^\\]]+)\\]\\([^)]+\\)\\s*([\\d\\-\\s,A-Za-zışğüöçİŞĞÜÖÇ]+(?:20\\d{2}))",
            Pattern.DOTALL
    );

    private final RestTemplate restTemplate;

    public IpoCalendarClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<IpoItem> fetchIpos() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            if (html == null) return List.of();

            return parseHtml(html);
        } catch (Exception e) {
            log.warn("Failed to fetch IPO calendar: {}", e.getMessage());
            return List.of();
        }
    }

    private List<IpoItem> parseHtml(String html) {
        List<IpoItem> result = new ArrayList<>();

        // Extract the main content section between "İlk Halka Arzlar" and "DAHA FAZLA GÖSTER"
        int start = html.indexOf("İlk Halka Arzlar");
        int end = html.indexOf("DAHA FAZLA GÖSTER");
        if (start == -1 || end == -1 || end <= start) {
            log.debug("Could not find IPO section in HTML");
            return result;
        }

        String section = html.substring(start, end);

        // Pattern: ticker in a span/div, company name in h3/a, date in next element
        // halkarz.com structure: <a href="/slug/">TICKER</a> <h3><a>Company</a></h3> date text
        Pattern p = Pattern.compile(
                "href=\"https://halkarz\\.com/([^\"]+)\"[^>]*>([A-Z0-9]{3,6})</a>\\s*" +
                "<h3[^>]*>\\s*<a[^>]*>([^<]+)</a>\\s*</h3>\\s*" +
                "([\\d\\-\\s,A-Za-zışğüöçİŞĞÜÖÇ]+(?:20\\d{2}))",
                Pattern.DOTALL
        );

        Matcher m = p.matcher(section);
        while (m.find() && result.size() < 20) {
            String slug   = m.group(1).trim();
            String ticker = m.group(2).trim();
            String name   = m.group(3).trim();
            String date   = m.group(4).trim().replaceAll("\\s+", " ");

            IpoItem item = new IpoItem();
            item.setTicker(ticker);
            item.setName(name);
            item.setDate(date);
            item.setUrl("https://halkarz.com/" + slug);
            result.add(item);
        }

        log.debug("Parsed {} IPO items from halkarz.com", result.size());
        return result;
    }
}
