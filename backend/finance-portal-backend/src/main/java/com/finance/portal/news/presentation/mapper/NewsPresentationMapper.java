package com.finance.portal.news.presentation.mapper;

import com.finance.portal.news.application.model.GoldNewsResult;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsPage;
import com.finance.portal.news.presentation.dto.NewsItemDto;
import com.finance.portal.news.presentation.dto.NewsResponse;
import org.springframework.stereotype.Component;

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
        return new NewsItemDto(
                article.getTitle(),
                article.getDescription(),
                article.getUrl(),
                article.getImageUrl(),
                article.getPublishedAt(),
                article.getSource(),
                article.getAuthor());
    }
}
