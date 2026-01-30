package com.finance.portal.presentation.dto;

import java.util.List;

public class NewsResponse {

    private List<NewsItemDto> items;
    private Integer totalCount;

    public NewsResponse() {
    }

    public NewsResponse(List<NewsItemDto> items, Integer totalCount) {
        this.items = items;
        this.totalCount = totalCount;
    }

    public List<NewsItemDto> getItems() {
        return items;
    }

    public void setItems(List<NewsItemDto> items) {
        this.items = items;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
}
