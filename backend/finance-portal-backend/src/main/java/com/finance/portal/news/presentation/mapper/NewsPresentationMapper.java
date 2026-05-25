package com.finance.portal.news.presentation.mapper;

import com.finance.portal.news.application.model.GoldNewsResult;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsDetail;
import com.finance.portal.news.application.model.NewsPage;
import com.finance.portal.news.application.model.NewsQueryResult;
import com.finance.portal.news.domain.NewsCategory;
import com.finance.portal.news.domain.NewsIdUtil;
import com.finance.portal.news.presentation.dto.NewsDetailResponse;
import com.finance.portal.news.presentation.dto.NewsItemDto;
import com.finance.portal.news.presentation.dto.NewsListResponse;
import com.finance.portal.news.presentation.dto.NewsResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NewsPresentationMapper {

    public NewsResponse toNewsResponse(NewsPage page) {
        List<NewsItemDto> items = page.getItems().stream()
                .map(this::toNewsItemDto)
                .collect(Collectors.toList());
        return new NewsResponse(
                items,
                page.getPage(),
                page.getPageSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public List<NewsItemDto> toNewsItemDtoList(List<NewsArticle> articles) {
        return articles.stream().map(this::toNewsItemDto).collect(Collectors.toList());
    }

    public Map<String, Object> toGoldNewsBody(GoldNewsResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", toNewsItemDtoList(result.getItems()));
        body.put("isFiltered", result.isFiltered());
        body.put("label", result.getLabel());
        return body;
    }

    public NewsItemDto toNewsItemDto(NewsArticle article) {
        NewsItemDto dto = new NewsItemDto(
                article.getTitle(),
                article.getDescription(),
                article.getUrl(),
                article.getImageUrl(),
                article.getPublishedAt(),
                article.getSource(),
                article.getAuthor());
        dto.setId(NewsIdUtil.idFor(article.getUrl()));
        dto.setCategory(article.getCategory());
        NewsCategory cat = NewsCategory.fromString(article.getCategory());
        dto.setCategoryLabel(cat != null ? cat.getLabel() : null);
        return dto;
    }

    public NewsListResponse toListResponse(NewsQueryResult result) {
        List<NewsItemDto> items = toNewsItemDtoList(result.items());

        // Kategori facet'leri: enum sırasında, yalnızca haberi olanlar
        List<NewsListResponse.CategoryFacet> categories = new ArrayList<>();
        for (NewsCategory c : NewsCategory.values()) {
            long count = result.categoryCounts().getOrDefault(c.name(), 0L);
            if (count > 0) {
                categories.add(new NewsListResponse.CategoryFacet(c.name(), c.getLabel(), count));
            }
        }

        return new NewsListResponse(
                items, result.page(), result.pageSize(), result.totalElements(),
                result.totalPages(), result.sources(), categories);
    }

    public NewsDetailResponse toDetailResponse(NewsDetail detail) {
        return new NewsDetailResponse(
                toNewsItemDto(detail.article()),
                toNewsItemDtoList(detail.related()),
                detail.content());
    }
}
