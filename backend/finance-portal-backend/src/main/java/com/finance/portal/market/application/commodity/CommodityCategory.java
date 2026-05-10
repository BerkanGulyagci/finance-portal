package com.finance.portal.market.application.commodity;

public enum CommodityCategory {

    ENERGY("Enerji"),
    AGRICULTURE("Tarım"),
    INDUSTRIAL_METALS("Sanayi Metalleri");

    private final String displayNameTr;

    CommodityCategory(String displayNameTr) {
        this.displayNameTr = displayNameTr;
    }

    public String getDisplayNameTr() {
        return displayNameTr;
    }
}
