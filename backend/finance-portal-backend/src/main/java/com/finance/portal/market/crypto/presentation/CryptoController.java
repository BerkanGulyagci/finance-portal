package com.finance.portal.market.crypto.presentation;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.crypto.application.CryptoMarketItem;
import com.finance.portal.market.crypto.application.CryptoMarketService;
import com.finance.portal.market.crypto.infrastructure.CoinGeckoClient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Crypto market API (TRY). Powered by CoinGecko.
 */
@Validated
@RestController
@RequestMapping("/api/market/crypto")
public class CryptoController {

    private final CryptoMarketService cryptoMarketService;
    private final CoinGeckoClient coinGeckoClient;

    public CryptoController(CryptoMarketService cryptoMarketService, CoinGeckoClient coinGeckoClient) {
        this.cryptoMarketService = cryptoMarketService;
        this.coinGeckoClient = coinGeckoClient;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CryptoMarketItem>>> getCryptos(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(250) int size,
            @RequestParam(defaultValue = "try") String currency
    ) {
        List<CryptoMarketItem> items = cryptoMarketService.getCryptos(page, size, currency);
        return ResponseEntity.ok(ApiResponse.success(items, "Crypto market list retrieved successfully"));
    }

    /** İlk 1000 coini tek seferde döner (cache'li). */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<CryptoMarketItem>>> getAllCoins(
            @RequestParam(defaultValue = "try") String currency
    ) {
        List<CryptoMarketItem> items = cryptoMarketService.getAllCoins(currency);
        return ResponseEntity.ok(ApiResponse.success(items, "All coins retrieved"));
    }

    /** Coin detayı: açıklama, linkler, ATH/ATL, arz bilgileri */
    @GetMapping("/{coinId}/detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCoinDetail(@PathVariable String coinId) {
        Map<String, Object> data = coinGeckoClient.fetchCoinDetail(coinId);
        return ResponseEntity.ok(ApiResponse.success(data, "Coin detail retrieved"));
    }

    /** OHLC mum grafiği verisi. days: 1, 7, 14, 30, 90, 180, 365 */
    @GetMapping("/{coinId}/ohlc")
    public ResponseEntity<ApiResponse<List<List<Number>>>> getOhlc(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "try") String currency
    ) {
        List<List<Number>> data = coinGeckoClient.fetchOhlc(coinId, days, currency);
        return ResponseEntity.ok(ApiResponse.success(data, "OHLC data retrieved"));
    }

    /** Fiyat geçmişi (line chart). days: 1, 7, 14, 30, 90, 180, 365 */
    @GetMapping("/{coinId}/chart")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChart(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "try") String currency
    ) {
        Map<String, Object> data = coinGeckoClient.fetchMarketChart(coinId, days, currency);
        return ResponseEntity.ok(ApiResponse.success(data, "Market chart retrieved"));
    }
}
