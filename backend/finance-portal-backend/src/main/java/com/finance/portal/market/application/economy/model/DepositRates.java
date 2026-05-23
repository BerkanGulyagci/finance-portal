package com.finance.portal.market.application.economy.model;

import java.math.BigDecimal;

/**
 * Güncel TL mevduat faiz oranları (vadeye göre, yıllık %) + güncel yıllık enflasyon —
 * mevduat getiri hesaplayıcısı için (reel getiri dahil).
 */
public class DepositRates {

    private BigDecimal upTo1Month;   // TP.TRY.MT01
    private BigDecimal upTo3Months;  // TP.TRY.MT02
    private BigDecimal upTo6Months;  // TP.TRY.MT03
    private BigDecimal upTo1Year;    // TP.TRY.MT04
    private BigDecimal inflationYoy; // TÜFE yıllık (reel getiri için)
    // Stopaj kademeleri (yıllık %, config'den) — canlı değil; mevzuat değişince application.yml'den güncellenir
    private BigDecimal stopaj6m;     // 6 aya kadar
    private BigDecimal stopaj1y;     // 6 ay – 1 yıl
    private BigDecimal stopajOver1y; // 1 yıldan uzun
    private String period;
    private String source;

    public DepositRates() {
    }

    public BigDecimal getUpTo1Month() { return upTo1Month; }
    public void setUpTo1Month(BigDecimal v) { this.upTo1Month = v; }

    public BigDecimal getUpTo3Months() { return upTo3Months; }
    public void setUpTo3Months(BigDecimal v) { this.upTo3Months = v; }

    public BigDecimal getUpTo6Months() { return upTo6Months; }
    public void setUpTo6Months(BigDecimal v) { this.upTo6Months = v; }

    public BigDecimal getUpTo1Year() { return upTo1Year; }
    public void setUpTo1Year(BigDecimal v) { this.upTo1Year = v; }

    public BigDecimal getInflationYoy() { return inflationYoy; }
    public void setInflationYoy(BigDecimal v) { this.inflationYoy = v; }

    public BigDecimal getStopaj6m() { return stopaj6m; }
    public void setStopaj6m(BigDecimal v) { this.stopaj6m = v; }

    public BigDecimal getStopaj1y() { return stopaj1y; }
    public void setStopaj1y(BigDecimal v) { this.stopaj1y = v; }

    public BigDecimal getStopajOver1y() { return stopajOver1y; }
    public void setStopajOver1y(BigDecimal v) { this.stopajOver1y = v; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
