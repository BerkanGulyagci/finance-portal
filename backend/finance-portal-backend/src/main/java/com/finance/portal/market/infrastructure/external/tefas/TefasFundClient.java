package com.finance.portal.market.infrastructure.external.tefas;

import com.finance.portal.market.application.funds.model.TefasFundItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TefasFundClient {

    private static final Logger log = LoggerFactory.getLogger(TefasFundClient.class);

    private static final String BASE_URL = "https://fundturkey.com.tr";
    private static final String INFO_ENDPOINT = BASE_URL + "/api/DB/BindHistoryInfo";
    private static final DateTimeFormatter TEFAS_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RestTemplate restTemplate;

    public TefasFundClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches all funds for the last business day from TEFAS.
     * @param kind YAT (mutual funds), EMK (pension), BYF (ETF)
     */
    public List<TefasFundItem> fetchFunds(String kind) {
        String date = getLastBusinessDay();
        log.debug("Fetching TEFAS funds for date: {}", date);
        return fetchFunds(kind, date, date, "");
    }

    public List<TefasFundItem> fetchFundByCode(String code) {
        String date = getLastBusinessDay();
        return fetchFunds("YAT", date, date, code.toUpperCase());
    }

    /** Returns last Friday if today is weekend, otherwise today */
    private String getLastBusinessDay() {
        LocalDate date = LocalDate.now();
        // If Saturday → go back 1 day to Friday
        // If Sunday → go back 2 days to Friday
        if (date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
            date = date.minusDays(1);
        } else if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            date = date.minusDays(2);
        }
        return date.format(TEFAS_DATE_FORMAT);
    }

    @SuppressWarnings("unchecked")
    private List<TefasFundItem> fetchFunds(String kind, String startDate, String endDate, String fundCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.set("Origin", BASE_URL);
        headers.set("Referer", BASE_URL + "/TarihselVeriler.aspx");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("fontip", kind);
        body.add("bastarih", startDate);
        body.add("bittarih", endDate);
        body.add("fonkod", fundCode);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(INFO_ENDPOINT, request, Map.class);
            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) return List.of();

            Object data = responseBody.get("data");
            if (!(data instanceof List)) return List.of();

            List<TefasFundItem> result = new ArrayList<>();
            for (Object item : (List<?>) data) {
                if (item instanceof Map) {
                    TefasFundItem fund = mapToFundItem((Map<?, ?>) item);
                    if (fund != null) result.add(fund);
                }
            }
            log.debug("Fetched {} TEFAS funds (kind={})", result.size(), kind);
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch TEFAS funds: {}", e.getMessage());
            return List.of();
        }
    }

    private TefasFundItem mapToFundItem(Map<?, ?> map) {
        try {
            TefasFundItem item = new TefasFundItem();
            item.setCode(str(map, "FONKODU"));
            item.setTitle(str(map, "FONUNVAN"));
            item.setPrice(parseBigDecimal(str(map, "FIYAT")));
            item.setDailyReturnPercent(parseBigDecimal(str(map, "GUNLUKGETIRI")));
            item.setMarketCap(parseBigDecimal(str(map, "PORTFOYBUYUKLUK")));
            item.setNumberOfInvestors(parseLong(str(map, "YATIRIMCISAYISI")));
            item.setDate(str(map, "TARIH"));
            return item;
        } catch (Exception e) {
            log.debug("Failed to map TEFAS fund item: {}", e.getMessage());
            return null;
        }
    }

    private String str(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private java.math.BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new java.math.BigDecimal(value.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.replace(".", "").replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
