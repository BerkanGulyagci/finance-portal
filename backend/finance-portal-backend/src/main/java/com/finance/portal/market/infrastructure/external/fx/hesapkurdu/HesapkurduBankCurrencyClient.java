package com.finance.portal.market.infrastructure.external.fx.hesapkurdu;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
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
import java.util.Map;

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
    private final CentralIntegrationLogService integrationLog;

    public HesapkurduBankCurrencyClient(RestTemplate restTemplate,
                                        com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                                        CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.integrationLog = integrationLog;
    }

    private void logIntegrationFailure(String eventType, String message, String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_HESAPKURDU,
                "getExchangeRates",
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                HesapkurduBankCurrencyClient.class.getName()
        );
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
            String httpStatus = String.valueOf(response.getStatusCode().value());
            if (bytes == null || bytes.length == 0) {
                log.warn("Hesapkurdu returned empty body");
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "Hesapkurdu bank rates: empty response body",
                        httpStatus,
                        Map.of("url", URL));
                return Collections.emptyList();
            }

            // UTF-8 ile decode — Türkçe karakterler korunur
            String json = new String(bytes, StandardCharsets.UTF_8);

            HesapkurduFxResponse parsed = objectMapper.readValue(json, HesapkurduFxResponse.class);

            List<HesapkurduFxItem> allItems = parsed.resolveDataList();
            if (!parsed.isSuccess() || allItems == null) {
                log.warn("Hesapkurdu response is unsuccessful or data is null");
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "Hesapkurdu bank rates: response unsuccessful or data field missing (JSON structure may have changed)",
                        httpStatus,
                        Map.of("success", parsed.isSuccess(), "dataNull", allItems == null));
                return Collections.emptyList();
            }

            List<HesapkurduFxItem> bankRates = allItems.stream()
                    .filter(item -> EXCHANGE_BANK.equals(item.getExchange()))
                    .toList();

            // Veri geldi ama "Bank" filtresinden 0 kayıt çıktı —
            // muhtemelen 'exchange' alanının değeri/adı değişti (sessiz hata).
            if (bankRates.isEmpty()) {
                log.warn("Hesapkurdu returned {} items but 0 matched exchange='{}'", allItems.size(), EXCHANGE_BANK);
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "Hesapkurdu bank rates: 0 records after 'Bank' filter (exchange field may have changed)",
                        httpStatus,
                        Map.of("totalItems", allItems.size(), "expectedExchange", EXCHANGE_BANK));
                return Collections.emptyList();
            }

            log.info("Hesapkurdu bank rates fetched: {} records", bankRates.size());
            return bankRates;

        } catch (Exception e) {
            log.error("Hesapkurdu bank rates fetch failed: {}", e.getMessage(), e);
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "Hesapkurdu bank rates fetch/parse failed: " + e.getMessage(),
                    null,
                    Map.of("exceptionClass", e.getClass().getSimpleName()));
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
