package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.bond.eurobond.EurobondService;
import com.finance.portal.market.application.bond.eurobond.model.EurobondChartPoint;
import com.finance.portal.market.application.bond.eurobond.model.EurobondDetail;
import com.finance.portal.market.application.bond.eurobond.model.EurobondSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Eurobond (Hazine dış borç tahvilleri) — ISIN listesi HMB'den, canlı veri Business Insider'dan.
 * Liste/detay/grafik EurobondService'te cache'lenir.
 */
@RestController
@RequestMapping("/api/market/bonds/global")
public class EurobondController {

    private final EurobondService eurobondService;

    public EurobondController(EurobondService eurobondService) {
        this.eurobondService = eurobondService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EurobondSummary>>> list(
            @org.springframework.web.bind.annotation.RequestParam(value = "tradeable",
                    required = false, defaultValue = "false") boolean tradeable) {
        List<EurobondSummary> list = eurobondService.list();
        if (tradeable) {
            // BI verisi olmayan eurobondlar işlem modalı için filtrelenir — fiyat olmadan
            // alış/satış K/Z hesaplanamaz, kullanıcı yanlış data girer.
            list = list.stream()
                    .filter(EurobondSummary::hasDetail)
                    .filter(s -> s.lastPrice() != null && s.lastPrice().signum() > 0)
                    .toList();
        }
        return ResponseEntity.ok(ApiResponse.success(list,
                "Eurobond listesi. Kaynak: HMB (ISIN) + Business Insider (canlı)"));
    }

    @GetMapping("/{isin}")
    public ResponseEntity<ApiResponse<EurobondDetail>> detail(@PathVariable String isin) {
        EurobondDetail d = eurobondService.detail(isin.trim().toUpperCase());
        if (d == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("Eurobond bulunamadı: " + isin));
        }
        return ResponseEntity.ok(ApiResponse.success(d, "Eurobond detayı. Kaynak: Business Insider"));
    }

    @GetMapping("/{isin}/chart")
    public ResponseEntity<ApiResponse<List<EurobondChartPoint>>> chart(
            @PathVariable String isin,
            @RequestParam(defaultValue = "1Y") String range) {
        List<EurobondChartPoint> pts = eurobondService.chart(isin.trim().toUpperCase(), range);
        return ResponseEntity.ok(ApiResponse.success(pts, "Eurobond fiyat serisi. Kaynak: Business Insider"));
    }
}
