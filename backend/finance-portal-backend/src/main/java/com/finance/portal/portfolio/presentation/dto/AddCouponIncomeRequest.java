package com.finance.portal.portfolio.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Manuel kupon ödeme kaydı isteği — DİBS/Kira Sertifikası için.
 *
 * <p>Sistem otomatik kupon ödemesi takip etmez; kullanıcı bankasından kupon alındığında
 * bu endpoint'ten kayıt girer. Realized gelir olarak portföye yansır, açık pozisyon
 * (nominal/cost) etkilenmez.
 */
@Getter
@Setter
@NoArgsConstructor
public class AddCouponIncomeRequest {

    /** Kuponu alınan bond/sukuk ISIN'i (ör. "TRT240227T17"). */
    @NotBlank(message = "symbol must not be blank")
    private String symbol;

    /** TL kupon tutarı (brüt) — komisyon/vergi'siz, kullanıcı bankasından gelen tutar. */
    @NotNull(message = "amount must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    /** Kupon ödeme tarihi (banka hesabına geçtiği gün). */
    @NotNull(message = "paymentDate must not be null")
    private LocalDateTime paymentDate;
}
