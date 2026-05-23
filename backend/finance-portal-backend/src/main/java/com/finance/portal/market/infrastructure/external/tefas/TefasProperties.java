package com.finance.portal.market.infrastructure.external.tefas;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TEFAS resmi fon API konfigürasyonu.
 * application.yml: market.funds.tefas.*
 *
 * Not: bearer-token, TEFAS'ın kendi public SPA'sinin kullandığı anonim token'dır
 * (kullanıcıya özel bir kimlik bilgisi değil). TEFAS değiştirirse buradan güncellenir.
 */
@Component
@ConfigurationProperties(prefix = "market.funds.tefas")
public class TefasProperties {

    private String historyUrl  = "https://www.tefas.gov.tr/api/funds/fonGnlBlgSiraliGetir";
    private String bearerToken = "ST-tefaswebwse3irfmSBj4iRAzGPbAlS94Se";
    private int    timeoutSeconds = 20;
    private int    chunkDays   = 28;   // TEFAS tek istekte ~30 günle sınırlı (31g boş döner)
    private int    parallelism = 4;    // chunk'lar paralel çekilir (TEFAS yoğun paralelde throttle eder)

    public String getHistoryUrl()            { return historyUrl; }
    public void setHistoryUrl(String v)      { this.historyUrl = v; }

    public String getBearerToken()           { return bearerToken; }
    public void setBearerToken(String v)     { this.bearerToken = v; }

    public int getTimeoutSeconds()           { return timeoutSeconds; }
    public void setTimeoutSeconds(int v)     { this.timeoutSeconds = v; }

    public int getChunkDays()                { return chunkDays; }
    public void setChunkDays(int v)          { this.chunkDays = v; }

    public int getParallelism()              { return parallelism; }
    public void setParallelism(int v)        { this.parallelism = v; }
}
