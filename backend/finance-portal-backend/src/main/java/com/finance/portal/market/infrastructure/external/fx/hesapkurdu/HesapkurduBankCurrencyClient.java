package com.finance.portal.market.infrastructure.external.fx.hesapkurdu;

import com.finance.portal.market.application.currency.model.HesapkurduFxItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Hesapkurdu döviz kuru API client.
 *
 * Endpoint: GET https://apigw.hesapkurdu.com/v1/forex/fx/getExchangeRates
 * Auth: Gerekmez — public endpoint.
 * Encoding: Response byte[] olarak alınıp UTF-8 ile decode edilir;
 *           Türkçe karakterler (İ, Ş, Ğ, Ö, Ü, ı) korunur.
 *
 * Sadece exchange == "Bank" olan kayıtları döndürür.
 */
@Component
public class HesapkurduBankCurrencyClient {

    private static final Logger log = LoggerFactory.getLogger(HesapkurduBankCurrencyClient.class);

    private static final String URL           = "https://apigw.hesapkurdu.com/v1/forex/fx/getExchangeRates";
    private static final String EXCHANGE_BANK = "Bank";

    private final RestTemplate restTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public HesapkurduBankCurrencyClient(RestTemplate restTemplate,
                                        com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Hesapkurdu'dan tüm döviz verilerini çeker ve sadece exchange == "Bank"
     * olan kayıtları döndürür.
     *
     * @return Banka döviz kuru listesi; hata durumunda boş liste.
     */
    public List<HesapkurduFxItem> fetchBankRates() {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URL, HttpMethod.GET, entity, byte[].class);

            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                log.warn("Hesapkurdu returned empty body");
                return Collections.emptyList();
            }

            // UTF-8 ile decode — Türkçe karakterler korunur
            String json = new String(bytes, StandardCharsets.UTF_8);

            HesapkurduFxResponse parsed = objectMapper.readValue(json, HesapkurduFxResponse.class);

            List<HesapkurduFxItem> allItems = parsed.resolveDataList();
            if (!parsed.isSuccess() || allItems == null) {
                log.warn("Hesapkurdu response is unsuccessful or data is null");
                return Collections.emptyList();
            }

            List<HesapkurduFxItem> bankRates = allItems.stream()
                    .filter(item -> EXCHANGE_BANK.equals(item.getExchange()))
                    .toList();

            log.info("Hesapkurdu bank rates fetched: {} records", bankRates.size());
            return bankRates;

        } catch (Exception e) {
            log.error("Hesapkurdu bank rates fetch failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setAcceptCharset(List.of(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        return headers;
    }
}
