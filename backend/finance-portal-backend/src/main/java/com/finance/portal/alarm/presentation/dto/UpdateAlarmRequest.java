package com.finance.portal.alarm.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Alarm güncelleme — tüm alanlar opsiyonel (yalnızca gönderilenler güncellenir).
 * {@code status} ile duraklat/yeniden etkinleştir (ACTIVE | DISABLED).
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateAlarmRequest {

    private String metric;
    private String direction;
    private BigDecimal threshold;
    private String frequency;
    private String status;
    private String note;
}
