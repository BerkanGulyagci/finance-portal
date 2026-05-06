package com.finance.portal.market.infrastructure.external.rasyonet;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Rasyonet/YatırımDirekt API konfigürasyonu.
 * application.yml: market.funds.rasyonet.*
 */
@Component
@ConfigurationProperties(prefix = "market.funds.rasyonet")
public class RasyonetProperties {

    private String filterUrl          = "https://api.rasyonet.com/service-yatirimdirekt-home/web-fund/fund-filter";
    private String cardUrl            = "https://api.rasyonet.com/service-yatirimdirekt-home/web-fund/card";
    private String osmanliBulletinUrl = "https://api.rasyonet.com/service-yatirimdirekt-home/web-menu/osmanli-fund-bulletin";
    private String sourceCode         = "TMF";
    private int    timeoutSeconds     = 15;

    public String getFilterUrl()                    { return filterUrl; }
    public void setFilterUrl(String v)              { this.filterUrl = v; }

    public String getCardUrl()                      { return cardUrl; }
    public void setCardUrl(String v)                { this.cardUrl = v; }

    public String getOsmanliBulletinUrl()           { return osmanliBulletinUrl; }
    public void setOsmanliBulletinUrl(String v)     { this.osmanliBulletinUrl = v; }

    public String getSourceCode()                   { return sourceCode; }
    public void setSourceCode(String v)             { this.sourceCode = v; }

    public int getTimeoutSeconds()                  { return timeoutSeconds; }
    public void setTimeoutSeconds(int v)            { this.timeoutSeconds = v; }
}
