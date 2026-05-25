package com.finance.portal.market.application.bond.eurobond.model;

/**
 * Business Insider SearchController_Suggest sonucundan çözülen tahvil referansı.
 * {@code IDs} alanı (pipe ile): slug | ISIN | ihraçcı | tam-ad | instrumentId.
 */
public record EurobondRef(
        String isin,
        String slug,        // detay sayfası slug'ı (lowercase) — /bonds/{slug}
        String name,
        String issuer,
        long instrumentId
) {}
