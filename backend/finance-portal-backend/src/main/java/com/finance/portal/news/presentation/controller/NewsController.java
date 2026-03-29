package com.finance.portal.news.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.news.application.service.NewsService;
import com.finance.portal.news.presentation.dto.NewsResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finance.portal.news.presentation.dto.NewsItemDto;
import java.util.List;

@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/news")
    public ResponseEntity<ApiResponse<NewsResponse>> getNews(
            @RequestParam(defaultValue = "business") String category,
            @RequestParam(defaultValue = "tr") String country,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword
    ) {
        NewsResponse newsResponse = newsService.getNews(category, country, page, pageSize, keyword);
        ApiResponse<NewsResponse> response = ApiResponse.success(
                newsResponse,
                "News retrieved successfully"
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/news/bloomberg-ht")
    public ResponseEntity<ApiResponse<List<NewsItemDto>>> getBloombergHtNews() {
        List<NewsItemDto> items = newsService.getBloombergHtNews();
        return ResponseEntity.ok(ApiResponse.success(items, "BloombergHT news retrieved successfully"));
    }
}
