package com.finance.portal.common.application.logging;

public final class IntegrationLogSupport {

    public static final String SERVICE_NAME = "finance-portal-backend";
    public static final String CATEGORY = "INTEGRATION";

    public static final String EVENT_EXTERNAL_API_FAILED = "EXTERNAL_API_FAILED";
    public static final String EVENT_EXTERNAL_API_RATE_LIMITED = "EXTERNAL_API_RATE_LIMITED";
    public static final String EVENT_EXTERNAL_API_EMPTY_RESPONSE = "EXTERNAL_API_EMPTY_RESPONSE";
    public static final String EVENT_EXTERNAL_API_PARSE_FAILED = "EXTERNAL_API_PARSE_FAILED";
    public static final String EVENT_EXTERNAL_API_FALLBACK_USED = "EXTERNAL_API_FALLBACK_USED";
    public static final String EVENT_MARKET_DATA_FETCH_FAILED = "MARKET_DATA_FETCH_FAILED";
    public static final String EVENT_NEWS_FETCH_FAILED = "NEWS_FETCH_FAILED";
    public static final String EVENT_SCHEDULER_JOB_FAILED = "SCHEDULER_JOB_FAILED";

    public static final String PROVIDER_YAHOO = "yahoo";
    public static final String PROVIDER_COINGECKO = "coingecko";
    public static final String PROVIDER_NEWSAPI = "newsapi";
    public static final String PROVIDER_BLOOMBERG_HT = "bloomberg_ht";
    public static final String PROVIDER_RASYONET = "rasyonet";
    public static final String PROVIDER_AKBANK_VIOP = "akbank_viop";
    public static final String PROVIDER_HESAPKURDU = "hesapkurdu";
    public static final String PROVIDER_MIDAS = "midas";
    public static final String PROVIDER_ISYATIRIM = "isyatirim";
    public static final String PROVIDER_BORSA_ISTANBUL = "borsa_istanbul";
    public static final String PROVIDER_CANLI_ALTIN = "canli_altin";
    public static final String PROVIDER_HALKARZ = "halkarz";
    public static final String PROVIDER_BINANCE = "binance";
    public static final String PROVIDER_TCMB = "tcmb";
    public static final String PROVIDER_OPEN_FX = "open_fx";
    public static final String PROVIDER_EVDS = "evds";
    public static final String PROVIDER_TUIK = "tuik";
    public static final String PROVIDER_EXTERNAL = "external";

    public static final String TRIGGER_SCHEDULER = "scheduler";
    public static final String TRIGGER_API_REQUEST = "api_request";

    private IntegrationLogSupport() {}
}
