package com.finance.portal.presentation.controller;

import com.finance.portal.application.service.NewsService;
import com.finance.portal.presentation.dto.ApiResponse;
import com.finance.portal.presentation.dto.NewsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/news")
    public ResponseEntity<ApiResponse<NewsResponse>> getNews() {
        NewsResponse newsResponse = newsService.getNews();
        ApiResponse<NewsResponse> response = ApiResponse.success(
                newsResponse,
                "News retrieved successfully"
        );
        return ResponseEntity.ok(response);
    }
}
