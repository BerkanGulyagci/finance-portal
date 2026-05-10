package com.finance.portal.market.infrastructure.external.precious;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BIST metal-fiyatlari.php endpoint'i.
 * Desteklenen metaller: AU (Altın), AG (Gümüş), PT (Platin), PD (Paladyum)
 *
 * <pre>
 * GET https://www.borsaistanbul.com/metal-fiyatlari.php
 *     ?op=fetchMetalFiyatlari
 *     &startDate={startDate}   yyyy-MM-dd
 *     &endDate={endDate}       yyyy-MM-dd
 *     &priceType=AU|AG|PT|PD
 * </pre>
 *
 * Kurallar:
 * - Sadece priceRef=MTL kayıtları kullanılır (REF dışlanır).
 * - Aynı tarih için USD/OZ, TRY/KG, EUR/OZ kayıtları gruplandırılır.
 * - validPrice = usdOns > 0 OR tryKg > 0
 * - tryGram = tryKg / 1000
 * - Dönen liste ASC (eski→yeni) sıralıdır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BistMetalFiyatlariClient {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final BigDecimal KG_TO_GRAM = new BigDecimal("1000");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${market.precious.bist.metal-url:https://www.borsaistanbul.com/metal-fiyatlari.php}")
    private String metalBaseUrl;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Tüm metaller için tarihsel referans fiyat verisi.
     * AU (Altın), AG (Gümüş), PT (Platin), PD (Paladyum) desteklenir.
     * Dönen liste ASC (eski→yeni) sıralıdır.
     */
    public List<BistMetalDailyPoint> fetchMetalPrices(
            PreciousMetalType metal, String startDate, String endDate) {

        String priceType = metal.getMetalPriceType(); // AU, AG, PT, PD

        String url = UriComponentsBuilder.fromHttpUrl(metalBaseUrl)
                .queryParam("op", "fetchMetalFiyatlari")
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .queryParam("priceType", priceType)
                .build(false)
                .toUriString();

        log.debug("Fetching BIST metal [{}]: {}", priceType, url);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set(HttpHeaders.ACCEPT, "application/json, */*");
            headers.set(HttpHeaders.REFERER, "https://www.borsaistanbul.com/");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("BIST metal [{}] returned empty body", priceType);
                return Collections.emptyList();
            }

            MetalApiResponse apiResponse = objectMapper.readValue(body, MetalApiResponse.class);

            if (!"success".equals(apiResponse.getStatus()) || apiResponse.getData() == null) {
                log.warn("BIST metal [{}] non-success: {}", priceType, apiResponse.getStatus());
                return Collections.emptyList();
            }

            List<BistMetalDailyPoint> points = groupByDate(metal, apiResponse.getData());

            // Endpoint ASC döndürüyor — kontrol için sırala
            points.sort((a, b) -> a.getDate().compareTo(b.getDate()));

            long validCount = points.stream().filter(BistMetalDailyPoint::isValidPrice).count();
            log.info("BIST metal [{}]: {} days ({} valid) [{} → {}]",
                    priceType, points.size(), validCount, startDate, endDate);
            return points;

        } catch (Exception e) {
            log.error("Failed to fetch BIST metal [{}] [{} → {}]: {}",
                    priceType, startDate, endDate, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Son N günlük veri.
     */
    public List<BistMetalDailyPoint> fetchMetalPricesLastDays(PreciousMetalType metal, int days) {
        LocalDate today = LocalDate.now();
        return fetchMetalPrices(metal,
                today.minusDays(days).format(DATE_FMT),
                today.format(DATE_FMT));
    }

    /**
     * En son geçerli fiyat noktası (son 10 gün içinde arar).
     */
    public BistMetalDailyPoint fetchLatestValidPoint(PreciousMetalType metal) {
        List<BistMetalDailyPoint> points = fetchMetalPricesLastDays(metal, 10);
        if (points == null || points.isEmpty()) return null;
        // ASC sıralı — sondan başa doğru ilk geçerli kaydı bul
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).isValidPrice()) return points.get(i);
        }
        return null;
    }

    // ── Gruplama ──────────────────────────────────────────────────────────────

    /**
     * Ham kayıtları tarihe göre gruplar.
     * Sadece priceRef=MTL kayıtları kullanılır.
     */
    private List<BistMetalDailyPoint> groupByDate(
            PreciousMetalType metal, List<BistMetalFiyatlariPoint> raw) {

        // Tarih → nokta map'i (insertion order korunur)
        Map<String, BistMetalDailyPoint> byDate = new LinkedHashMap<>();

        for (BistMetalFiyatlariPoint r : raw) {
            // REF kayıtlarını dışla
            if (!"MTL".equals(r.getPriceRef())) continue;
            if (r.getPriceDate() == null || r.getPriceValue() == null) continue;

            BistMetalDailyPoint pt = byDate.computeIfAbsent(r.getPriceDate(), date -> {
                BistMetalDailyPoint p = new BistMetalDailyPoint();
                p.setMetalType(metal);
                p.setDate(date);
                return p;
            });

            BigDecimal val = r.getPriceValue();
            String currency = r.getPriceCurrency();
            String weight   = r.getPriceWeight();

            if ("USD".equals(currency) && "OZ".equals(weight)) {
                pt.setUsdOns(val);
            } else if ("TRY".equals(currency) && "KG".equals(weight)) {
                pt.setTryKg(val);
                if (val != null && val.compareTo(ZERO) > 0) {
                    pt.setTryGram(val.divide(KG_TO_GRAM, 4, RoundingMode.HALF_UP));
                }
            } else if ("EUR".equals(currency) && "OZ".equals(weight)) {
                pt.setEurOns(val);
            }
        }

        // validPrice hesapla
        for (BistMetalDailyPoint pt : byDate.values()) {
            boolean usdOk = pt.getUsdOns() != null && pt.getUsdOns().compareTo(ZERO) > 0;
            boolean tryOk = pt.getTryKg()  != null && pt.getTryKg().compareTo(ZERO)  > 0;
            pt.setValidPrice(usdOk || tryOk);
        }

        return new ArrayList<>(byDate.values());
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class MetalApiResponse {
        @JsonProperty("status") private String status;
        @JsonProperty("source") private String source;
        @JsonProperty("data")   private List<BistMetalFiyatlariPoint> data = new ArrayList<>();
    }
}
