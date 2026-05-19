package com.finance.portal.market.infrastructure.external.precious;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.market.application.precious.model.BistPreciousMetalPoint;
import com.finance.portal.market.application.precious.model.PreciousMetalType;
import com.finance.portal.market.application.precious.model.PriceUnit;
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
import java.util.List;

/**
 * Borsa İstanbul Kıymetli Madenler Veri Sorgulama — genel client.
 *
 * <pre>
 * GET https://www.borsaistanbul.com/veri-sorgulama.php
 *     ?op=fetchVeriSorgulama
 *     &bastar={startDate}
 *     &bittar={endDate}
 *     &pazart={PreciousMetalType.pazart}   SA=Altın, SG=Gümüş, ...
 *     &f_tipit={PriceUnit.fTipit}          L/K=TL/Kg, $/O=USD/Ons
 * </pre>
 *
 * BIST DESC döndürür → bu client ASC (eski→yeni) döndürür.
 * validPrice filtresi: closeRaw > 0 OR weightedAverageRaw > 0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BistPreciousMetalsClient {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final BigDecimal KG_TO_GRAM = new BigDecimal("1000");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${market.gold.bist.url:https://www.borsaistanbul.com/veri-sorgulama.php}")
    private String bistBaseUrl;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Belirtilen metal ve birim için tarihsel veri çeker.
     * Dönen liste ASC (eski→yeni) sıralıdır.
     * validPrice=false kayıtlar dahil edilir — servis katmanı filtreler.
     */
    public List<BistPreciousMetalPoint> fetchHistory(
            PreciousMetalType metal, PriceUnit unit,
            String startDate, String endDate) {
        return fetchBist(metal, unit, startDate, endDate);
    }

    /**
     * Son N günlük veri.
     */
    public List<BistPreciousMetalPoint> fetchHistoryLastDays(
            PreciousMetalType metal, PriceUnit unit, int days) {
        LocalDate today = LocalDate.now();
        return fetchHistory(metal, unit,
                today.minusDays(days).format(DATE_FMT),
                today.format(DATE_FMT));
    }

    /**
     * En son geçerli fiyat noktası (son 10 gün içinde arar).
     * validPrice=false kayıtları atlar.
     */
    public BistPreciousMetalPoint fetchLatestValidPoint(
            PreciousMetalType metal, PriceUnit unit) {
        List<BistPreciousMetalPoint> points = fetchHistoryLastDays(metal, unit, 10);
        if (points == null || points.isEmpty()) return null;
        // ASC sıralı — sondan başa doğru ilk geçerli kaydı bul
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).isValidPrice()) return points.get(i);
        }
        return null;
    }

    // ── Core fetch ────────────────────────────────────────────────────────────

    private List<BistPreciousMetalPoint> fetchBist(
            PreciousMetalType metal, PriceUnit unit,
            String startDate, String endDate) {

        String url = UriComponentsBuilder.fromHttpUrl(bistBaseUrl)
                .queryParam("op", "fetchVeriSorgulama")
                .queryParam("bastar", startDate)
                .queryParam("bittar", endDate)
                .queryParam("pazart", metal.getPazart())
                .queryParam("f_tipit", unit.getFTipit())
                .build(false)
                .toUriString();

        log.debug("Fetching BIST {} [{}]: {}", metal.getDisplayName(), unit.getFTipit(), url);

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
                log.warn("BIST {} [{}] returned empty body", metal, unit);
                return Collections.emptyList();
            }

            BistApiResponse apiResponse = objectMapper.readValue(body, BistApiResponse.class);

            if (!"success".equals(apiResponse.getStatus()) || apiResponse.getData() == null) {
                log.warn("BIST {} [{}] non-success: {}", metal, unit, apiResponse.getStatus());
                return Collections.emptyList();
            }

            List<BistPreciousMetalPoint> points = apiResponse.getData();

            // Metal ve birim bilgisini set et
            points.forEach(p -> {
                p.setMetalType(metal);
                p.setUnit(unit);
            });

            // validPrice hesapla
            points.forEach(this::computeValidPrice);

            // Türetilmiş değerleri hesapla
            if (!unit.isOns()) {
                points.forEach(this::enrichWithGramValues);
            } else {
                points.forEach(this::enrichWithOnsValues);
            }

            // BIST DESC → ASC
            Collections.reverse(points);

            long validCount = points.stream().filter(BistPreciousMetalPoint::isValidPrice).count();
            log.info("BIST {} [{}]: {} points ({} valid) [{} → {}]",
                    metal.getDisplayName(), unit.getFTipit(),
                    points.size(), validCount, startDate, endDate);
            return points;

        } catch (Exception e) {
            log.error("Failed to fetch BIST {} [{}] [{} → {}]: {}",
                    metal, unit, startDate, endDate, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Hesaplama ─────────────────────────────────────────────────────────────

    /**
     * validPrice = closeRaw > 0 OR weightedAverageRaw > 0
     */
    private void computeValidPrice(BistPreciousMetalPoint p) {
        boolean closeOk = p.getCloseRaw() != null && p.getCloseRaw().compareTo(ZERO) > 0;
        boolean waOk    = p.getWeightedAverageRaw() != null && p.getWeightedAverageRaw().compareTo(ZERO) > 0;
        p.setValidPrice(closeOk || waOk);
    }

    /** TL/Kg → TL/Gram (/1000) */
    private void enrichWithGramValues(BistPreciousMetalPoint p) {
        if (p.getCloseRaw() != null)
            p.setGramClose(divide(p.getCloseRaw()));
        if (p.getWeightedAverageRaw() != null)
            p.setGramWeightedAverage(divide(p.getWeightedAverageRaw()));
        if (p.getLowRaw() != null)
            p.setGramLow(divide(p.getLowRaw()));
        if (p.getHighRaw() != null)
            p.setGramHigh(divide(p.getHighRaw()));
    }

    /** USD/Ons — ham değerleri kopyala */
    private void enrichWithOnsValues(BistPreciousMetalPoint p) {
        p.setCloseUsdOns(p.getCloseRaw());
        p.setWeightedAverageUsdOns(p.getWeightedAverageRaw());
        p.setLowUsdOns(p.getLowRaw());
        p.setHighUsdOns(p.getHighRaw());
        p.setVolumeUsd(p.getVolumeRaw());
    }

    private BigDecimal divide(BigDecimal kgValue) {
        if (kgValue == null || kgValue.compareTo(ZERO) == 0) return ZERO;
        return kgValue.divide(KG_TO_GRAM, 4, RoundingMode.HALF_UP);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BistApiResponse {
        @JsonProperty("status")  private String status;
        @JsonProperty("source")  private String source;
        @JsonProperty("data")    private List<BistPreciousMetalPoint> data = new ArrayList<>();
    }
}
