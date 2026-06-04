package com.finance.portal.news.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Filtrelenmiş haber listesi yanıtı + filtre UI için facet'ler (kaynaklar, kategoriler).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NewsListResponse {

    private List<NewsItemDto> items;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private List<String> sources;
    private List<CategoryFacet> categories;

    /** Kategori filtresi seçeneği: anahtar + görünen etiket + o kategorideki haber sayısı. */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryFacet {
        private String key;
        private String label;
        private long count;
    }
}
