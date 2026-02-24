package com.finance.portal.news.presentation.dto;

import java.util.List;

public class NewsResponse {

    private List<NewsItemDto> items;
    private int page;
    private int pageSize;
    private int totalElements;
    private int totalPages;

    public NewsResponse() {
    }

    public NewsResponse(List<NewsItemDto> items, int page, int pageSize, int totalElements, int totalPages) {
        this.items = items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<NewsItemDto> getItems() {
        return items;
    }

    public void setItems(List<NewsItemDto> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(int totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
