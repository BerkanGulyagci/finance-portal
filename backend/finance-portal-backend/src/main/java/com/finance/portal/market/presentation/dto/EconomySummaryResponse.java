package com.finance.portal.market.presentation.dto;

import java.util.List;

/**
 * Türkiye ekonomisi özet yanıtı — göstergeler kategoriye göre gruplanmış.
 *
 * @param source kaynak ("TCMB EVDS")
 * @param groups kategori grupları (katalog sırasında)
 */
public record EconomySummaryResponse(
        String source,
        List<CategoryGroup> groups
) {

    /**
     * Bir kategori ve altındaki göstergeler.
     *
     * @param category   kategori anahtarı (örn. {@code INFLATION})
     * @param label      kategori görünen adı (örn. "Enflasyon")
     * @param indicators kategorideki göstergeler
     */
    public record CategoryGroup(
            String category,
            String label,
            List<EconomyIndicatorDto> indicators
    ) {
    }
}
