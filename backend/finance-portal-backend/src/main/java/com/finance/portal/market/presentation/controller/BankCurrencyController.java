package com.finance.portal.market.presentation.controller;

import com.finance.portal.market.application.currency.BankCurrencyRateDto;
import com.finance.portal.market.application.currency.BankCurrencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Banka döviz kurları REST controller.
 *
 * Endpoints:
 *   GET /api/market/currency/banks                  → Tüm banka kurları
 *   GET /api/market/currency/banks?currency=USD     → Döviz koduna göre filtre
 *   GET /api/market/currency/banks/{bankName}       → Banka adına göre filtre
 */
// Sonar S6863: bu controller hata durumunda da HTTP 200 + body {"success":false} dönüyor.
// Mevcut frontend bu sözleşmeye dayalı; tüm controller'ları ControllerAdvice tabanlı
// 4xx/5xx error response'a geçirmek ayrı bir refactor görevi (#API-error-uniformity).
@SuppressWarnings({"java:S6863", "java:S1135"})
@RestController
@RequestMapping("/api/market/currency")
public class BankCurrencyController {

    private static final Logger log = LoggerFactory.getLogger(BankCurrencyController.class);

    private final BankCurrencyService bankCurrencyService;

    public BankCurrencyController(BankCurrencyService bankCurrencyService) {
        this.bankCurrencyService = bankCurrencyService;
    }

    /**
     * Tüm banka kurlarını döndürür.
     * currency parametresi verilirse o dövize göre filtreler.
     *
     * GET /api/market/currency/banks
     * GET /api/market/currency/banks?currency=USD
     */
    @GetMapping("/banks")
    public ResponseEntity<Map<String, Object>> getBankRates(
            @RequestParam(required = false) String currency) {

        try {
            List<BankCurrencyRateDto> rates = (currency != null && !currency.isBlank())
                    ? bankCurrencyService.getBankRatesByCurrency(currency.trim())
                    : bankCurrencyService.getAllBankRates();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", rates,
                    "count", rates.size()
            ));
        } catch (Exception e) {
            log.error("Error fetching bank rates: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "data", List.of(),
                    "count", 0,
                    "error", "Banka kurları alınamadı"
            ));
        }
    }

    /**
     * Belirli bir bankanın kurlarını döndürür (kısmi isim eşleşmesi).
     *
     * GET /api/market/currency/banks/{bankName}
     * Örn: /api/market/currency/banks/Garanti
     */
    @GetMapping("/banks/{bankName}")
    public ResponseEntity<Map<String, Object>> getBankRatesByBankName(
            @PathVariable String bankName) {

        try {
            List<BankCurrencyRateDto> rates = bankCurrencyService.getBankRatesByBankName(bankName);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", rates,
                    "count", rates.size()
            ));
        } catch (Exception e) {
            log.error("Error fetching bank rates for bank '{}': {}", bankName, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "data", List.of(),
                    "count", 0,
                    "error", "Banka kurları alınamadı"
            ));
        }
    }
}
