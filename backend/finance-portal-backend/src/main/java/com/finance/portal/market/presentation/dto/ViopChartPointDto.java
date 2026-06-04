package com.finance.portal.market.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * VİOP grafik veri noktası — frontend'e dönen DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViopChartPointDto {

    /** Unix timestamp (milliseconds) */
    private Long timestamp;

    /** ISO-8601 formatında tarih/saat (örn: 2026-04-27T10:00:00) */
    private String dateTime;

    /** Sözleşme fiyatı */
    private BigDecimal value;
}
