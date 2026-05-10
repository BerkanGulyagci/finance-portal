package com.finance.portal.market.infrastructure.external.precious;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * BIST Kıymetli Madenler metal tipi.
 *
 * veri-sorgulama.php için: pazart alanı (SA, SG)
 * metal-fiyatlari.php için: priceType alanı (AU, AG, PT, PD)
 */
@Getter
@RequiredArgsConstructor
public enum PreciousMetalType {

    GOLD("SA",      "AU",  "Altın"),
    SILVER("SG",    "AG",  "Gümüş"),
    PLATINUM("PT",  "PT",  "Platin"),
    PALLADIUM("PD", "PD",  "Paladyum");

    /** BIST veri-sorgulama.php pazart parametresi (SA, SG) */
    private final String pazart;

    /** BIST metal-fiyatlari.php priceType parametresi (AU, AG, PT, PD) */
    private final String metalPriceType;

    /** Türkçe görünen ad */
    private final String displayName;
}
