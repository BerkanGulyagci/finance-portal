package com.finance.portal.news.presentation.controller;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.news.application.model.GoldNewsResult;
import com.finance.portal.news.application.model.NewsArticle;
import com.finance.portal.news.application.model.NewsDetail;
import com.finance.portal.news.application.model.NewsQueryResult;
import com.finance.portal.news.application.service.NewsAggregatorService;
import com.finance.portal.news.application.service.NewsService;
import com.finance.portal.news.presentation.dto.NewsDetailResponse;
import com.finance.portal.news.presentation.dto.NewsItemDto;
import com.finance.portal.news.presentation.dto.NewsListResponse;
import com.finance.portal.news.presentation.mapper.NewsPresentationMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class NewsController {

    private final NewsService newsService;
    private final NewsAggregatorService aggregatorService;
    private final NewsPresentationMapper newsMapper;

    public NewsController(NewsService newsService,
                         NewsAggregatorService aggregatorService,
                         NewsPresentationMapper newsMapper) {
        this.newsService = newsService;
        this.aggregatorService = aggregatorService;
        this.newsMapper = newsMapper;
    }

    /**
     * Tüm sağlayıcılardan toplanmış, filtreli + sayfalı haber listesi.
     * Filtreler: category (NewsCategory), source (kaynak adı), q (arama), range (24h/7d/30d).
     * excludeCategory: varsayılan görünümde bir kategoriyi dışlar (ör. ECONOMY); kullanıcı o
     * kategoriyi category ile açıkça seçtiğinde geçersiz olur (kategori dropdown'da kalır).
     */
    @GetMapping("/news")
    public ResponseEntity<ApiResponse<NewsListResponse>> getNews(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String excludeCategory,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String range,
            @RequestParam(defaultValue = "TR") String region,
            @RequestParam(required = false) String lang,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int pageSize
    ) {
        Long fromMillis = rangeToFromMillis(range);
        NewsQueryResult result = aggregatorService.query(category, excludeCategory, source, q, region, fromMillis, null, page, pageSize, lang);
        NewsListResponse body = newsMapper.toListResponse(result);
        return ResponseEntity.ok(ApiResponse.success(body, "Haberler getirildi"));
    }

    /**
     * Giriş yapan kullanıcının portföyüne göre "Size Özel" haberler (sembol/isim/varlık türü eşleşmesi).
     * Token yoksa (anonim) boş liste döner.
     */
    @GetMapping("/news/for-me")
    public ResponseEntity<ApiResponse<NewsListResponse>> getForMe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String lang,
            @RequestParam(defaultValue = "9") @Min(1) @Max(30) int limit) {
        String userId = jwt != null ? jwt.getSubject() : null;
        NewsQueryResult result = aggregatorService.forUser(userId, lang, limit);
        NewsListResponse body = newsMapper.toListResponse(result);
        return ResponseEntity.ok(ApiResponse.success(body, "Size özel haberler getirildi"));
    }

    /** Tek haber detayı + ilgili haberler (id = URL'den türetilen kararlı kimlik). */
    @GetMapping("/news/detail/{id}")
    public ResponseEntity<ApiResponse<NewsDetailResponse>> getNewsDetail(
            @PathVariable String id,
            @RequestParam(required = false) String lang) {
        NewsDetail detail = aggregatorService.getById(id, lang);
        if (detail == null) {
            throw new ResourceNotFoundException("Haber bulunamadı: " + id);
        }
        NewsDetailResponse body = newsMapper.toDetailResponse(detail);
        return ResponseEntity.ok(ApiResponse.success(body, "Haber detayı getirildi"));
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

    private static Long rangeToFromMillis(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        Instant now = Instant.now();
        return switch (range.trim().toLowerCase()) {
            case "24h", "1d" -> now.minus(24, ChronoUnit.HOURS).toEpochMilli();
            case "7d", "1w" -> now.minus(7, ChronoUnit.DAYS).toEpochMilli();
            case "30d", "1m" -> now.minus(30, ChronoUnit.DAYS).toEpochMilli();
            default -> null;
        };
    }
}
