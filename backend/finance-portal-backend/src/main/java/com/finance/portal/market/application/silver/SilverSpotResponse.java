package com.finance.portal.market.application.silver;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * GET /api/silver/spot response.
 * Kaynak: Borsa İstanbul Kıymetli Madenler (pazart=SG).
 */
@Data
@NoArgsConstructor
public class SilverSpotResponse implements Serializable {

    private String source;
    private boolean official;
    private boolean fallback;
    private boolean stale;
    private String lastUpdated;
    private String bistDate;

    /** En son geçerli fiyat tarihi (son gün 0 ise bir önceki geçerli gün) */
    private String lastValidDate;

    private String disclaimer;

    // ── TL/Kg ham değerler ────────────────────────────────────────────────────

    private BigDecimal closeTryKg;
    private BigDecimal weightedAverageTryKg;
    private BigDecimal lowTryKg;
    private BigDecimal highTryKg;
    private BigDecimal volumeTry;
    private BigDecimal quantityKg;
    private Integer transactionCount;

    // ── TL/Gram hesaplanan değerler ───────────────────────────────────────────

    /** weightedAverageTryKg / 1000 — ana referans */
    private BigDecimal silverGramTry;

    /** closeTryKg / 1000 */
    private BigDecimal silverGramCloseTry;

    /** lowTryKg / 1000 */
    private BigDecimal silverGramLowTry;

    /** highTryKg / 1000 */
    private BigDecimal silverGramHighTry;

    // ── USD/Ons ───────────────────────────────────────────────────────────────

    private BigDecimal silverUsdOns;
    private BigDecimal silverUsdOnsHigh;
    private BigDecimal silverUsdOnsLow;
    private BigDecimal silverUsdOnsWeightedAverage;
}
