package com.finance.portal.market.application.stock;

import java.util.List;

public class StockPageResponse {

    private List<StockSummary> content;
    private int page;
    private int size;
    private int totalElements;
    private int totalPages;

    public StockPageResponse() {
    }

    public List<StockSummary> getContent() {
        return content;
    }

    public void setContent(List<StockSummary> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
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

