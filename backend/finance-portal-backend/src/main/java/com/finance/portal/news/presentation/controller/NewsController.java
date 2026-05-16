package com.finance.portal.news.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.news.application.model.GoldNewsResult;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsPage;
import com.finance.portal.news.application.service.NewsService;
import com.finance.portal.news.presentation.dto.NewsItemDto;
import com.finance.portal.news.presentation.dto.NewsResponse;
import com.finance.portal.news.presentation.mapper.NewsPresentationMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class NewsController {

    private final NewsService newsService;
    private final NewsPresentationMapper newsMapper;

    public NewsController(NewsService newsService, NewsPresentationMapper newsMapper) {
        this.newsService = newsService;
        this.newsMapper = newsMapper;
    }

    @GetMapping("/news")
    public ResponseEntity<ApiResponse<NewsResponse>> getNews(
            @RequestParam(defaultValue = "business") String category,
            @RequestParam(defaultValue = "tr") String country,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword
    ) {
        NewsPage newsPage = newsService.getNews(category, country, page, pageSize, keyword);
        NewsResponse newsResponse = newsMapper.toNewsResponse(newsPage);
        return ResponseEntity.ok(ApiResponse.success(newsResponse, "News retrieved successfully"));
    }

    @GetMapping("/news/bloomberg-ht")
    public ResponseEntity<ApiResponse<List<NewsItemDto>>> getBloombergHtNews() {
        List<NewsArticle> articles = newsService.getBloombergHtNews();
        List<NewsItemDto> items = newsMapper.toNewsItemDtoList(articles);
        return ResponseEntity.ok(ApiResponse.success(items, "BloombergHT news retrieved successfully"));
    }

    @GetMapping("/news/gold")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGoldNews() {
        GoldNewsResult result = newsService.getGoldNews();
        Map<String, Object> body = newsMapper.toGoldNewsBody(result);
        return ResponseEntity.ok(ApiResponse.success(body, "Gold news retrieved successfully"));
    }
}
