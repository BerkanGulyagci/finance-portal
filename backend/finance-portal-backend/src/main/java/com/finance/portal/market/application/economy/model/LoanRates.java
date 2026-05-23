package com.finance.portal.market.application.economy.model;

import java.math.BigDecimal;

/**
 * Güncel kredi faiz oranları (yıllık %, TCMB EVDS akım verisi) — kredi taksit hesaplayıcısı için.
 */
public class LoanRates {

    private BigDecimal personal;    // İhtiyaç (TP.KTF10)
    private BigDecimal vehicle;     // Taşıt (TP.KTF11)
    private BigDecimal housing;     // Konut (TP.KTF12)
    private BigDecimal commercial;  // Ticari (TP.KTF17)
    private String period;          // EVDS son dönem (örn. "08-05-2026")
    private String source;

    public LoanRates() {
    }

    public BigDecimal getPersonal() { return personal; }
    public void setPersonal(BigDecimal personal) { this.personal = personal; }

    public BigDecimal getVehicle() { return vehicle; }
    public void setVehicle(BigDecimal vehicle) { this.vehicle = vehicle; }

    public BigDecimal getHousing() { return housing; }
    public void setHousing(BigDecimal housing) { this.housing = housing; }

    public BigDecimal getCommercial() { return commercial; }
    public void setCommercial(BigDecimal commercial) { this.commercial = commercial; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
