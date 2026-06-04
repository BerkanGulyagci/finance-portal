package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.economy.DepositRateService;
import com.finance.portal.market.application.economy.EconomyChartService;
import com.finance.portal.market.application.economy.EconomyService;
import com.finance.portal.market.application.economy.LoanRateService;
import com.finance.portal.market.application.economy.model.DepositRates;
import com.finance.portal.market.application.economy.model.EconomyChartSeries;
import com.finance.portal.market.application.economy.model.EconomyIndicator;
import com.finance.portal.market.application.economy.model.LoanRates;
import com.finance.portal.market.presentation.dto.EconomyIndicatorDto;
import com.finance.portal.market.presentation.dto.EconomySummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Türkiye ekonomisi özet göstergeleri.
 *
 * <p>{@code GET /api/v1/market/economy} — TÜFE/ÜFE enflasyon, faizler, GSYİH büyüme,
 * cari denge, rezerv, kur, işsizlik, bütçe vb. tek yanıtta, kategoriye göre gruplanmış.
 * Kaynak: TCMB EVDS.
 */
@RestController
@RequestMapping("/api/v1/market/economy")
public class EconomyController {

    private final EconomyService economyService;
    private final EconomyChartService economyChartService;
    private final LoanRateService loanRateService;
    private final DepositRateService depositRateService;

    public EconomyController(EconomyService economyService,
                            EconomyChartService economyChartService,
                            LoanRateService loanRateService,
                            DepositRateService depositRateService) {
        this.economyService = economyService;
        this.economyChartService = economyChartService;
        this.loanRateService = loanRateService;
        this.depositRateService = depositRateService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EconomySummaryResponse>> getEconomySummary() {
        List<EconomyIndicator> indicators = economyService.getSummary();
        EconomySummaryResponse response = toResponse(indicators);
        return ResponseEntity.ok(ApiResponse.success(response, "Türkiye ekonomi göstergeleri"));
    }

    /**
     * Tek bir göstergenin grafik (zaman serisi) verisi.
     * {@code GET /api/v1/market/economy/series?key=tufe}
     */
    @GetMapping("/series")
    public ResponseEntity<ApiResponse<EconomyChartSeries>> getSeries(
            @RequestParam("key") String key,
            @RequestParam(name = "full", defaultValue = "false") boolean full) {
        EconomyChartSeries series = full
                ? economyChartService.getFullChartSeries(key)
                : economyChartService.getChartSeries(key);
        return ResponseEntity.ok(ApiResponse.success(series, "Gösterge zaman serisi"));
    }

    /**
     * Tüm göstergelerin grafik serileri (rapor sayfası — tek çağrı).
     * {@code GET /api/v1/market/economy/charts}
     */
    @GetMapping("/charts")
    public ResponseEntity<ApiResponse<List<EconomyChartSeries>>> getAllSeries() {
        List<EconomyChartSeries> all = economyChartService.getAllChartSeries();
        return ResponseEntity.ok(ApiResponse.success(all, "Tüm gösterge zaman serileri"));
    }

    /**
     * Güncel kredi faiz oranları (ihtiyaç/taşıt/konut/ticari) — kredi taksit hesaplayıcısı için.
     * {@code GET /api/v1/market/economy/loan-rates}
     */
    @GetMapping("/loan-rates")
    public ResponseEntity<ApiResponse<LoanRates>> getLoanRates() {
        return ResponseEntity.ok(ApiResponse.success(loanRateService.getLoanRates(), "Güncel kredi faiz oranları"));
    }

    /**
     * Güncel TL mevduat faiz oranları (vadeye göre) + yıllık enflasyon — mevduat getiri hesaplayıcısı için.
     * {@code GET /api/v1/market/economy/deposit-rates}
     */
    @GetMapping("/deposit-rates")
    public ResponseEntity<ApiResponse<DepositRates>> getDepositRates() {
        return ResponseEntity.ok(ApiResponse.success(depositRateService.getDepositRates(), "Güncel mevduat faiz oranları"));
    }

    /** Göstergeleri kategori sırasını koruyarak gruplar. */
    private EconomySummaryResponse toResponse(List<EconomyIndicator> indicators) {
        Map<String, EconomySummaryResponse.CategoryGroup> groups = new LinkedHashMap<>();
        Map<String, List<EconomyIndicatorDto>> items = new LinkedHashMap<>();

        for (EconomyIndicator ind : indicators) {
            items.computeIfAbsent(ind.getCategory(), k -> new ArrayList<>()).add(toDto(ind));
            groups.computeIfAbsent(ind.getCategory(),
                    k -> new EconomySummaryResponse.CategoryGroup(
                            ind.getCategory(), ind.getCategoryLabel(), null));
        }

        List<EconomySummaryResponse.CategoryGroup> result = new ArrayList<>(groups.size());
        for (EconomySummaryResponse.CategoryGroup g : groups.values()) {
            result.add(new EconomySummaryResponse.CategoryGroup(
                    g.category(), g.label(), items.get(g.category())));
        }
        return new EconomySummaryResponse(EconomyService.SOURCE, result);
    }

    private EconomyIndicatorDto toDto(EconomyIndicator i) {
        return new EconomyIndicatorDto(
                i.getKey(),
                i.getLabel(),
                i.getUnit(),
                i.getFrequency(),
                i.getValue(),
                i.getPeriod(),
                i.getPreviousValue(),
                i.getChangePercent(),
                i.getAbsoluteChange(),
                i.getYoyChangePercent(),
                i.isAvailable(),
                i.isPreferAbsolute(),
                i.getSeriesCode()
        );
    }
}
