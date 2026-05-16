package com.finance.portal.portfolio.presentation.dto;

import com.finance.portal.common.domain.AssetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * İzleme listesi kalemi response DTO'su.
 * <p>
 * Şu an sadece DB alanlarını içerir.
 * İleride fiyat bilgileri (lastPrice, open, high, low, change, changePercent, volume, asOf)
 * market servislerinden çekilerek bu DTO'ya eklenir.
 */
@Getter
@Setter
@NoArgsConstructor
public class WatchlistItemResponse {

    private UUID id;
    private String symbol;
    private AssetType assetType;
    private String notes;
    private LocalDateTime addedAt;
    private BigDecimal startPrice;
    private String startCurrency;

    // ── Live market fields (optional) ─────────────────────────────────────────
    private BigDecimal lastPrice;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal change;
    private BigDecimal changePercent;
    private Long volume;
    private String currency;
    private LocalDateTime asOf;

    // ── FX (optional) ─────────────────────────────────────────────────────────
    private BigDecimal buy;
    private BigDecimal sell;

    // ── Bond (optional) ───────────────────────────────────────────────────────
    private Integer remainingDays;
    private BigDecimal couponRate;

    // ── Fund / TEFAS (optional, izleme listesi fon satırları) ─────────────────
    private String fundName;
    private String fundType;
    private BigDecimal fundReturnOneMonth;
    private BigDecimal fundReturnThreeMonths;
    private BigDecimal fundReturnYtd;
    private BigDecimal fundReturnOneYear;
    private Integer fundRiskLevel;
}
