package com.finance.portal.market.infrastructure.external.viop;

import com.finance.portal.market.application.viop.ViopContract;
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
import java.util.regex.Pattern;

@Component
public class AkbankViopClient {

    private static final Logger log = LoggerFactory.getLogger(AkbankViopClient.class);
    private static final String URL = "https://yatirim.akbank.com/tr-tr/viop/Sayfalar/tum-viop-sozlesmeleri.aspx";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private final RestTemplate restTemplate;

    public AkbankViopClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<ViopContract> fetchContracts() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "tr-TR,tr;q=0.9");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            if (html == null) return List.of();

            return parseHtml(html);
        } catch (Exception e) {
            log.warn("Failed to fetch VIOP contracts: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ViopContract> parseHtml(String html) {
        List<ViopContract> result = new ArrayList<>();

        // Strip all HTML tags and split into non-empty tokens
        String[] lines = html.replaceAll("<[^>]+>", "\n").split("\n");
        List<String> tokens = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty()) tokens.add(t);
        }

        // Each contract block = 10 consecutive tokens:
        // [0] name (contains "Vadeli")
        // [1] changePercent (starts with %)
        // [2] lastPrice (numeric)
        // [3] high (numeric)
        // [4] low (numeric)
        // [5] openPositionCount (numeric)
        // [6] openPositionChange (numeric, may be negative)
        // [7] settlementPrice (numeric)
        // [8] prevSettlementPrice (numeric)
        // [9] time (HH:mm:ss)

        Pattern timePattern = Pattern.compile("^\\d{2}:\\d{2}:\\d{2}$");
        Pattern numericPattern = Pattern.compile("^-?[\\d.,]+$");
        Pattern pctPattern = Pattern.compile("^%[+\\-]?[\\d.,]+$");

        for (int i = 0; i < tokens.size() - 9; i++) {
            String t0 = tokens.get(i);
            // Contract name must contain "Vadeli"
            if (!t0.contains("Vadeli")) continue;

            String t1 = tokens.get(i + 1);
            String t9 = tokens.get(i + 9);

            // Validate: t1 should be a percent, t9 should be a time
            if (!pctPattern.matcher(t1).matches()) continue;
            if (!timePattern.matcher(t9).matches()) continue;

            ViopContract c = new ViopContract();
            c.setName(t0.replaceAll("\\s+", " ").trim());
            c.setChangePercent(t1);
            c.setLastPrice(tokens.get(i + 2));
            c.setHigh(tokens.get(i + 3));
            c.setLow(tokens.get(i + 4));
            c.setOpenPositionCount(tokens.get(i + 5));
            c.setOpenPositionChange(tokens.get(i + 6));
            c.setSettlementPrice(tokens.get(i + 7));
            c.setPrevSettlementPrice(tokens.get(i + 8));
            c.setTime(t9);
            result.add(c);
            i += 9; // skip to next block
        }

        log.info("Parsed {} VIOP contracts", result.size());
        return result;
    }
}
