package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.crypto.CryptoBinanceChartService;
import com.finance.portal.market.application.crypto.CryptoMarketService;
import com.finance.portal.market.application.crypto.CryptoYahooChartService;
import com.finance.portal.market.application.crypto.FearGreedService;
import com.finance.portal.market.application.stock.StockChartResponse;
import com.finance.portal.market.application.crypto.model.CryptoChartCandle;
import com.finance.portal.market.application.crypto.model.CryptoMarketItem;
import com.finance.portal.market.application.crypto.model.FearGreedPoint;
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

@Validated
@RestController
@RequestMapping("/api/v1/market/crypto")
public class CryptoController {

    private final CryptoMarketService cryptoMarketService;
    private final CryptoBinanceChartService cryptoBinanceChartService;
    private final CryptoYahooChartService cryptoYahooChartService;
    private final FearGreedService fearGreedService;

    public CryptoController(CryptoMarketService cryptoMarketService,
                            CryptoBinanceChartService cryptoBinanceChartService,
                            CryptoYahooChartService cryptoYahooChartService,
                            FearGreedService fearGreedService) {
        this.cryptoMarketService = cryptoMarketService;
        this.cryptoBinanceChartService = cryptoBinanceChartService;
        this.cryptoYahooChartService = cryptoYahooChartService;
        this.fearGreedService = fearGreedService;
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

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<CryptoMarketItem>>> getAllCoins(
            @RequestParam(defaultValue = "try") String currency
    ) {
        List<CryptoMarketItem> items = cryptoMarketService.getAllCoins(currency);
        return ResponseEntity.ok(ApiResponse.success(items, "All coins retrieved"));
    }

    @GetMapping("/{coinId}/detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCoinDetail(
            @PathVariable String coinId,
            @RequestParam(required = false) String lang
    ) {
        Map<String, Object> data = cryptoMarketService.getCoinDetail(coinId, lang);
        return ResponseEntity.ok(ApiResponse.success(data, "Coin detail retrieved"));
    }

    @GetMapping("/{coinId}/ohlc")
    public ResponseEntity<ApiResponse<List<List<Number>>>> getOhlc(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "7") String days,
            @RequestParam(defaultValue = "try") String currency
    ) {
        List<List<Number>> data = cryptoMarketService.getOhlc(coinId, days, currency);
        return ResponseEntity.ok(ApiResponse.success(data, "OHLC data retrieved"));
    }

    /**
     * USD/EUR 5Y / Tüm için Yahoo Finance OHLC (BTC-USD, BTC-EUR).
     */
    @GetMapping("/{symbol}/yahoo/ohlc")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getYahooOhlc(
            @PathVariable String symbol,
            @RequestParam String range,
            @RequestParam String currency
    ) {
        List<Map<String, Object>> rows = cryptoYahooChartService.getOhlc(symbol, range, currency);
        String msg = rows.isEmpty()
                ? "No Yahoo OHLC for this symbol/range/currency"
                : "Yahoo crypto OHLC retrieved";
        return ResponseEntity.ok(ApiResponse.success(rows, msg));
    }

    /**
     * USD/EUR 5Y / Tüm için Yahoo Finance çizgi grafik (kapanış serisi).
     */
    @GetMapping("/{symbol}/yahoo/chart")
    public ResponseEntity<ApiResponse<StockChartResponse>> getYahooChart(
            @PathVariable String symbol,
            @RequestParam String range,
            @RequestParam String currency
    ) {
        StockChartResponse chart = cryptoYahooChartService.getLineChart(symbol, range, currency);
        if (chart == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "No Yahoo chart data"));
        }
        return ResponseEntity.ok(ApiResponse.success(chart, "Yahoo crypto chart retrieved"));
    }

    /**
     * TL çizgi grafiği (5Y / Tüm) — Yahoo USD kapanışı × Yahoo USD/TRY ({@code TRY=X}).
     * CoinGecko TRY ~1 yıl, Binance {@code BTCTRY} ~2019 ile sınırlı; bu uç eski (2014+) veriyi
     * TL'ye çevirip döndürür. Yalnız çizgi grafikte kullanılır (mum grafiğini etkilemez).
     */
    @GetMapping("/{symbol}/yahoo/chart-try")
    public ResponseEntity<ApiResponse<StockChartResponse>> getYahooTryChart(
            @PathVariable String symbol,
            @RequestParam String range
    ) {
        StockChartResponse chart = cryptoYahooChartService.getTryLineViaUsd(symbol, range);
        if (chart == null) {
            return ResponseEntity.ok(ApiResponse.success(null, "No Yahoo TRY-converted chart data"));
        }
        return ResponseEntity.ok(ApiResponse.success(chart, "Yahoo crypto TRY-converted chart retrieved"));
    }

    /**
     * TRY 5Y / Tüm için Binance Spot klines (BTCTRY vb.). Diğer para birimi veya aralıkta boş liste döner.
     */
    @GetMapping("/{symbol}/candles")
    public ResponseEntity<ApiResponse<List<CryptoChartCandle>>> getChartCandles(
            @PathVariable String symbol,
            @RequestParam String range,
            @RequestParam(defaultValue = "try") String currency
    ) {
        List<CryptoChartCandle> candles = cryptoBinanceChartService.getChartCandles(symbol, range, currency);
        String msg = candles.isEmpty()
                ? "No Binance TRY candles for this symbol/range (use Yahoo fallback on client)"
                : "Binance TRY chart candles retrieved";
        return ResponseEntity.ok(ApiResponse.success(candles, msg));
    }

    /**
     * Crypto Fear &amp; Greed Index (piyasa-geneli, coinId YOK) — son {@code days} günlük seri.
     * Kaynak alternative.me; nokta başına {@code timestamp} MİLİSANİYE, {@code value} 0-100,
     * {@code classification} metin. Frontend bunu coin fiyatıyla karşılaştırmalı çizer.
     */
    @GetMapping("/fear-greed")
    public ResponseEntity<ApiResponse<List<FearGreedPoint>>> getFearGreed(
            @RequestParam(defaultValue = "90") @Min(1) @Max(365) int days
    ) {
        List<FearGreedPoint> points = fearGreedService.getFearGreed(days);
        return ResponseEntity.ok(ApiResponse.success(points, "Fear & Greed index retrieved"));
    }

    @GetMapping("/{coinId}/chart")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChart(
            @PathVariable String coinId,
            @RequestParam(defaultValue = "7") String days,
            @RequestParam(defaultValue = "try") String currency,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) String aggregate
    ) {
        Map<String, Object> data = cryptoMarketService.getMarketChart(
                coinId, days, currency, interval, aggregate);
        return ResponseEntity.ok(ApiResponse.success(data, "Market chart retrieved"));
    }
}
