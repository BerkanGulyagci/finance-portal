package com.finance.portal.news.presentation.dto;

import java.util.List;

/**
 * Filtrelenmiş haber listesi yanıtı + filtre UI için facet'ler (kaynaklar, kategoriler).
 */
public class NewsListResponse {

    private List<NewsItemDto> items;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private List<String> sources;
    private List<CategoryFacet> categories;

    public NewsListResponse() {
    }

    public NewsListResponse(List<NewsItemDto> items, int page, int pageSize, long totalElements,
                            int totalPages, List<String> sources, List<CategoryFacet> categories) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.sources = sources;
        this.categories = categories;
    }

    public List<NewsItemDto> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<String> getSources() {
        return sources;
    }

    public List<CategoryFacet> getCategories() {
        return categories;
    }

    /** Kategori filtresi seçeneği: anahtar + görünen etiket + o kategorideki haber sayısı. */
    public static class CategoryFacet {
        private String key;
        private String label;
        private long count;

        public CategoryFacet() {
        }

        public CategoryFacet(String key, String label, long count) {
            this.key = key;
            this.label = label;
            this.count = count;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public long getCount() {
            return count;
        }
    }
}
