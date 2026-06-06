package com.finance.portal.assistant.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Warren AI sohbet asistanı konfigürasyonu (application.yml: assistant.*).
 * Sağlayıcı-bağımsız, OpenAI-uyumlu chat-completions API'si; varsayılan Groq (ücretsiz).
 * api-key yalnızca .env.local'den gelir (ASSISTANT_API_KEY); boşsa asistan devre dışı sayılır.
 */
@Component
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

    private boolean enabled = true;
    private String provider = "groq";
    private String apiUrl = "https://api.groq.com/openai/v1/chat/completions";
    private String apiKey = "";
    private String model = "llama-3.3-70b-versatile";
    private int timeoutSeconds = 30;
    private int maxTokens = 700;
    private double temperature = 0.4;
    private int anonMessageLimit = 2;
    private int userDailyLimit = 50;
    /**
     * Anonim rate-limit IP'sini X-Forwarded-For'dan çıkarırken güvenilen (uygulama önündeki) proxy
     * SAYISI. GCP harici HTTP(S) yük dengeleyici (GKE GCE Ingress) XFF'i KORUR ve sona "client-ip,lb-ip"
     * EKLER → gerçek istemci IP'si SONDAN 2. (=lb'den hemen önceki) girdidir; leftmost girdi
     * istemci-kontrollü/sahtelenebilir. trustedProxyCount=1 → sondan 1 hop (lb) atlanır, ondan önceki
     * (gerçek client) alınır. Doğrudan erişimde (XFF yok) remoteAddr kullanılır. nginx-only/başka
     * topolojide ortama göre ayarlanır (ASSISTANT_TRUSTED_PROXY_COUNT).
     */
    private int trustedProxyCount = 1;
    private int historyLimit = 10;
    private int maxInputChars = 2000;
    /** Araç KULLANMAYAN tek-soru yanıtlarının (terim/nav) Redis cache süresi (sn). 0 = cache kapalı. */
    private long cacheTtlSeconds = 21600;

    // ── Gemini fallback (Groq kota/hata verince devreye girer) ──
    private boolean fallbackEnabled = true;
    private String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models";
    private String geminiApiKey = "";
    private String geminiModel = "gemini-2.5-flash";

    /**
     * Model zinciri: "saglayici:model" adımları, sırayla denenir; biri kota/hata verince sonrakine düşülür.
     * saglayici = groq | gemini. Boşsa eski tekil davranış (Groq → Gemini) kullanılır.
     * Örn: gemini:gemini-2.5-flash, gemini:gemini-2.5-flash-lite, groq:llama-3.3-70b-versatile
     */
    private java.util.List<String> chain = new java.util.ArrayList<>();

    /** Birincil (Groq) kullanılabilir mi: açık VE anahtar tanımlı. */
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /** Gemini fallback kullanılabilir mi: açık VE Gemini anahtarı tanımlı. */
    public boolean isGeminiUsable() {
        return fallbackEnabled && geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public boolean isFallbackEnabled()       { return fallbackEnabled; }
    public void setFallbackEnabled(boolean v){ this.fallbackEnabled = v; }

    public String getGeminiApiUrl()          { return geminiApiUrl; }
    public void setGeminiApiUrl(String v)    { this.geminiApiUrl = v; }

    public String getGeminiApiKey()          { return geminiApiKey; }
    public void setGeminiApiKey(String v)    { this.geminiApiKey = v; }

    public String getGeminiModel()           { return geminiModel; }
    public void setGeminiModel(String v)     { this.geminiModel = v; }

    public java.util.List<String> getChain()             { return chain; }
    public void setChain(java.util.List<String> v)       { this.chain = v != null ? v : new java.util.ArrayList<>(); }

    public boolean isEnabled()              { return enabled; }
    public void setEnabled(boolean v)       { this.enabled = v; }

    public String getProvider()             { return provider; }
    public void setProvider(String v)       { this.provider = v; }

    public String getApiUrl()               { return apiUrl; }
    public void setApiUrl(String v)         { this.apiUrl = v; }

    public String getApiKey()               { return apiKey; }
    public void setApiKey(String v)         { this.apiKey = v; }

    public String getModel()                { return model; }
    public void setModel(String v)          { this.model = v; }

    public int getTimeoutSeconds()          { return timeoutSeconds; }
    public void setTimeoutSeconds(int v)    { this.timeoutSeconds = v; }

    public int getMaxTokens()               { return maxTokens; }
    public void setMaxTokens(int v)         { this.maxTokens = v; }

    public double getTemperature()          { return temperature; }
    public void setTemperature(double v)    { this.temperature = v; }

    public int getAnonMessageLimit()        { return anonMessageLimit; }
    public void setAnonMessageLimit(int v)  { this.anonMessageLimit = v; }

    public int getUserDailyLimit()          { return userDailyLimit; }
    public void setUserDailyLimit(int v)    { this.userDailyLimit = v; }

    public int getTrustedProxyCount()       { return trustedProxyCount; }
    public void setTrustedProxyCount(int v) { this.trustedProxyCount = Math.max(0, v); }

    public int getHistoryLimit()            { return historyLimit; }
    public void setHistoryLimit(int v)      { this.historyLimit = v; }

    public int getMaxInputChars()           { return maxInputChars; }
    public void setMaxInputChars(int v)     { this.maxInputChars = v; }

    public long getCacheTtlSeconds()        { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(long v)  { this.cacheTtlSeconds = v; }
}
