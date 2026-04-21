package com.finance.portal.market.infrastructure.external.bond;

import com.finance.portal.market.application.bond.BondItem;
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
public class ZiraatBondClient {

    private static final Logger log = LoggerFactory.getLogger(ZiraatBondClient.class);
    private static final String URL = "https://www.ziraatbank.com.tr/tr/bireysel/yatirim/bono-tahvil/hazine-bonosu-devlet-tahvili";

    private final RestTemplate restTemplate;

    public ZiraatBondClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<BondItem> fetchBonds() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            if (html == null || html.isBlank()) return List.of();

            return parseHtml(html);
        } catch (Exception e) {
            log.warn("Failed to fetch Ziraat bond data: {}", e.getMessage());
            return List.of();
        }
    }

    private List<BondItem> parseHtml(String html) {
        List<BondItem> result = new ArrayList<>();

        // Strip HTML tags and split into tokens
        String[] lines = html.replaceAll("<[^>]+>", "\n").split("\n");
        List<String> tokens = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) tokens.add(t);
        }

        // Each bond row = 8 consecutive tokens:
        // [0] name (TRB... or TRT...)
        // [1] maturityDate (dd.MM.yyyy)
        // [2] daysToMaturity (integer)
        // [3] currency (TL)
        // [4] buyPrice (decimal with comma)
        // [5] buyRate (decimal)
        // [6] sellPrice (decimal with comma)
        // [7] sellRate (decimal)

        Pattern bondNamePattern = Pattern.compile("^TR[BT]\\d+[A-Z]\\d+$");
        Pattern datePattern = Pattern.compile("^\\d{2}\\.\\d{2}\\.\\d{4}\\s*$");
        Pattern numericPattern = Pattern.compile("^-?[\\d,\\.]+$");

        for (int i = 0; i < tokens.size() - 7; i++) {
            String t0 = tokens.get(i).trim();
            if (!bondNamePattern.matcher(t0).matches()) continue;

            String t1 = tokens.get(i + 1).trim();
            if (!datePattern.matcher(t1).matches()) continue;

            try {
                BondItem item = new BondItem();
                item.setName(t0);
                item.setMaturityDate(t1.trim());

                String daysStr = tokens.get(i + 2).trim();
                try { item.setDaysToMaturity(Integer.parseInt(daysStr)); } catch (NumberFormatException ignored) {}

                item.setCurrency(tokens.get(i + 3).trim());
                item.setBuyPrice(tokens.get(i + 4).trim());
                item.setBuyRate(tokens.get(i + 5).trim());
                item.setSellPrice(tokens.get(i + 6).trim());
                item.setSellRate(tokens.get(i + 7).trim());

                result.add(item);
                i += 7;
            } catch (Exception e) {
                log.debug("Failed to parse bond row at index {}", i);
            }
        }

        log.info("Parsed {} bond items from Ziraat Bank", result.size());
        return result;
    }
}
