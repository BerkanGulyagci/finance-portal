package com.finance.portal.news.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class NewsApiResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("totalResults")
    private Integer totalResults;

    @JsonProperty("articles")
    private List<NewsApiArticle> articles;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NewsApiArticle {

        @JsonProperty("title")
        private String title;

        @JsonProperty("description")
        private String description;

        @JsonProperty("url")
        private String url;

        @JsonProperty("urlToImage")
        private String urlToImage;

        @JsonProperty("publishedAt")
        private String publishedAt;

        @JsonProperty("source")
        private NewsApiSource source;

        @JsonProperty("author")
        private String author;

        @JsonProperty("content")
        private String content;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NewsApiSource {

        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;
    }
}
