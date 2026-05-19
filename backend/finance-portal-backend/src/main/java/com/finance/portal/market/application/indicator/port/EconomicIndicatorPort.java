package com.finance.portal.market.application.indicator.port;

/**
 * Ekonomik gösterge (TCMB politika faizi vb.) adaptör portu.
 */
public interface EconomicIndicatorPort {

    /**
     * Güncel TCMB politika faizini döndürür (örn. "37"); başarısızlıkta null.
     */
    String fetchPolicyRate();
}
