package com.finance.portal.news.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NewsResponse {

    private List<NewsItemDto> items;
    private int page;
    private int pageSize;
    private int totalElements;
    private int totalPages;
}
