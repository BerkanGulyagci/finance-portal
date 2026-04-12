package com.finance.portal.market.crypto.presentation;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.crypto.application.CryptoMarketItem;
import com.finance.portal.market.crypto.application.CryptoMarketService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Crypto market API (TRY). Powered by CoinGecko.
 */
@Validated
@RestController
@RequestMapping("/api/market/crypto")
public class CryptoController {

    private final CryptoMarketService cryptoMarketService;

    public CryptoController(CryptoMarketService cryptoMarketService) {
        this.cryptoMarketService = cryptoMarketService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CryptoMarketItem>>> getCryptos(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(250) int size
    ) {
        List<CryptoMarketItem> items = cryptoMarketService.getCryptos(page, size);
        ApiResponse<List<CryptoMarketItem>> response = ApiResponse.success(
                items,
                "Crypto market list retrieved successfully"
        );
        return ResponseEntity.ok(response);
    }
}
