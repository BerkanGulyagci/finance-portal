package com.finance.portal.portfolio.application.whatif;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * "Ne Olurdu?" zaman serisi sonucu — seçilen varlık (ya da tüm portföy) için, yatırılan paranın
 * zaman içinde gerçek / enflasyon / altın / dolar / mevduat senaryolarındaki TL değeri.
 */
@Getter
@Setter
@NoArgsConstructor
public class WhatIfSeriesResult {

    private UUID portfolioId;
    private String currency = "TRY";

    /** Seçim: "ALL" (tüm portföy) ya da varlık adı/sembolü. */
    private String scope;
    /** Tek varlık seçildiyse asset türü/sembolü (ALL'da null). */
    private String assetType;
    private String symbol;
    private String label;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate asOf;

    /** Hangi senaryolar için veri var (frontend efsane/çizgi gösterimi). */
    private List<String> availableScenarios;

    /** Verisi bulunan kullanıcı-eklemeli serbest kıyaslar (id = "ASSETTYPE|SYMBOL"). */
    private List<String> availableBenchmarks;

    private List<WhatIfSeriesPoint> points;
}
