package com.finance.portal.news.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Haberde tespit edilen ilgili varlık (frontend grafik linki için).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RelatedAssetDto {
    private String symbol;
    private String name;
    /** STOCK | CRYPTO | FX — frontend doğru detay route'unu kurar. */
    private String type;
}
