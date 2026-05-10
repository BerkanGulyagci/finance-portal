package com.finance.portal.market.application.commodity;

/**
 * Yahoo Finance üzerinden çekilen global emtia futures sembolleri.
 * Kıymetli madenler (AU, AG, PT, PD) bu enum'da yer almaz — onlar BIST kaynaklıdır.
 *
 * needsCentConversion=true → Yahoo USX (US Cent) döndürür, displayPrice = rawPrice / 100
 */
public enum CommoditySymbol {

    // ── Enerji ────────────────────────────────────────────────────────────────
    CL_F("CL=F", "WTI Ham Petrol",   "WTI Crude Oil",   CommodityCategory.ENERGY,            "Varil",  false, true),
    BZ_F("BZ=F", "Brent Ham Petrol", "Brent Crude Oil", CommodityCategory.ENERGY,            "Varil",  false, true),
    NG_F("NG=F", "Doğal Gaz",        "Natural Gas",     CommodityCategory.ENERGY,            "MMBtu",  false, true),

    // ── Sanayi Metalleri ──────────────────────────────────────────────────────
    HG_F("HG=F", "Bakır",            "Copper",          CommodityCategory.INDUSTRIAL_METALS, "Pound",  false, true),

    // ── Tarım ─────────────────────────────────────────────────────────────────
    ZW_F("ZW=F", "Buğday",           "Wheat",           CommodityCategory.AGRICULTURE,       "Bushel", true,  true),
    ZC_F("ZC=F", "Mısır",            "Corn",            CommodityCategory.AGRICULTURE,       "Bushel", true,  true),
    KC_F("KC=F", "Kahve",            "Coffee",          CommodityCategory.AGRICULTURE,       "Pound",  true,  true),
    CC_F("CC=F", "Kakao",            "Cocoa",           CommodityCategory.AGRICULTURE,       "Ton",    false, true),
    CT_F("CT=F", "Pamuk",            "Cotton",          CommodityCategory.AGRICULTURE,       "Pound",  true,  true);

    private final String           symbol;
    private final String           displayNameTr;
    private final String           displayNameEn;
    private final CommodityCategory category;
    private final String           unit;
    /** true → Yahoo USX döndürür, /100 ile USD'ye çevir */
    private final boolean          needsCentConversion;
    private final boolean          enabled;

    CommoditySymbol(String symbol, String displayNameTr, String displayNameEn,
                    CommodityCategory category, String unit,
                    boolean needsCentConversion, boolean enabled) {
        this.symbol              = symbol;
        this.displayNameTr       = displayNameTr;
        this.displayNameEn       = displayNameEn;
        this.category            = category;
        this.unit                = unit;
        this.needsCentConversion = needsCentConversion;
        this.enabled             = enabled;
    }

    public String            getSymbol()              { return symbol; }
    public String            getDisplayNameTr()       { return displayNameTr; }
    public String            getDisplayNameEn()       { return displayNameEn; }
    public CommodityCategory getCategory()            { return category; }
    public String            getUnit()                { return unit; }
    public boolean           isNeedsCentConversion()  { return needsCentConversion; }
    public boolean           isEnabled()              { return enabled; }

    /**
     * Yahoo sembolünden enum bul.
     * "CL=F", "cl=f", "CL-F" formatlarını kabul eder.
     */
    public static CommoditySymbol fromSymbol(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Sembol boş olamaz");
        }
        String normalized = raw.trim().toUpperCase().replace("-", "=");
        for (CommoditySymbol cs : values()) {
            if (cs.symbol.equalsIgnoreCase(normalized)) return cs;
        }
        throw new IllegalArgumentException("Desteklenmeyen veya devre dışı emtia sembolü: " + raw);
    }
}
