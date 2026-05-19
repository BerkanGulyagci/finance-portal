package com.finance.portal.market.application.precious.model;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * BIST Kıymetli Madenler f_tipit parametresi.
 */
@Getter
@RequiredArgsConstructor
public enum PriceUnit {

    TRY_KG("L/K",  "TL/Kg",   false),
    USD_ONS("$/O", "USD/Ons",  true),
    EUR_ONS("E/O", "EUR/Ons",  true);  // gerekirse

    /** BIST f_tipit query parametresi */
    private final String fTipit;

    /** Görünen birim etiketi */
    private final String label;

    /** Ons birimi mi? (false = kg birimi) */
    private final boolean ons;
}
