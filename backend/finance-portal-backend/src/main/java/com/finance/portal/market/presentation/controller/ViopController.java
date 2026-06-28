package com.finance.portal.market.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.viop.ViopContract;
import com.finance.portal.market.application.viop.ViopIndexCodeMapper;
import com.finance.portal.market.application.viop.ViopService;
import com.finance.portal.market.presentation.dto.ViopContractSpecDto;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market/viop")
public class ViopController {

    private static final Logger log = LoggerFactory.getLogger(ViopController.class);

    private final ViopService viopService;
    private final ViopContractSpecRegistry specRegistry;
    private final ViopIndexCodeMapper indexCodeMapper;

    public ViopController(ViopService viopService,
                          ViopContractSpecRegistry specRegistry,
                          ViopIndexCodeMapper indexCodeMapper) {
        this.viopService = viopService;
        this.specRegistry = specRegistry;
        this.indexCodeMapper = indexCodeMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ViopContract>>> getContracts() {
        List<ViopContract> contracts = viopService.getAllContracts();
        log.info("VIOP contracts count: {}", contracts.size());
        if (!contracts.isEmpty()) {
            log.info("First contract: {}", contracts.get(0).getName());
        }
        return ResponseEntity.ok(ApiResponse.success(contracts, "VIOP contracts retrieved successfully"));
    }

    /**
     * Bir VİOP kontrat sembolü için spec'i döndürür (multiplier, marginRate, currency).
     * AddTransactionModal canlı önizleme kartı için kullanılır.
     *
     * @param symbol F_AKBNK0626 veya "USDTRY (30 HAZ 26) VADELI" formatı
     * @return spec; bulunamazsa fallback (multiplier=1, marginRate=0.15) + found=false
     */
    @GetMapping("/spec")
    public ResponseEntity<ApiResponse<ViopContractSpecDto>> getSpec(@RequestParam String symbol) {
        ViopContractSpec spec = specRegistry.resolveOrFallback(symbol);
        boolean found = specRegistry.resolveBySymbol(symbol).isPresent();
        String isYatirimCode = indexCodeMapper.toIsYatirimEndeksCode(symbol).orElse(null);
        var dto = new ViopContractSpecDto(
                spec.code(),
                spec.assetClass() != null ? spec.assetClass().name() : null,
                spec.multiplier(),
                spec.marginRate(),
                spec.currency(),
                spec.settlementType() != null ? spec.settlementType().name() : null,
                found,
                isYatirimCode
        );
        return ResponseEntity.ok(ApiResponse.success(dto, "VIOP contract spec"));
    }
}
