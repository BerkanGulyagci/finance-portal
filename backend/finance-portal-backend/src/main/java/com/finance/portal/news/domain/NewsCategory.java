package com.finance.portal.news.domain;

/**
 * Haber kategorileri. Farklı sağlayıcılardan gelen haberler bu ortak kategorilere
 * normalize edilir (kaynak feed varsayılanı + {@link NewsClassifier} anahtar-kelime eşleşmesi).
 */
public enum NewsCategory {

    ECONOMY("Genel Ekonomi"),
    STOCKS("Hisse / Borsa"),
    FX("Döviz"),
    BONDS("Tahvil / Faiz"),
    CRYPTO("Kripto"),
    COMMODITIES("Altın / Emtia"),
    WORLD("Dünya");

    private final String label;

    NewsCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Güvenli parse: bilinmeyen/boş değerlerde null döner (filtre = tümü). */
    public static NewsCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NewsCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
