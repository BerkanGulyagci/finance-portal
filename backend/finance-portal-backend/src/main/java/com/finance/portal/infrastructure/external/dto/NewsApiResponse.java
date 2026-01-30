package com.finance.portal.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class NewsApiResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("totalResults")
    private Integer totalResults;

    @JsonProperty("articles")
    private List<NewsApiArticle> articles;

    public NewsApiResponse() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(Integer totalResults) {
        this.totalResults = totalResults;
    }

    public List<NewsApiArticle> getArticles() {
        return articles;
    }

    public void setArticles(List<NewsApiArticle> articles) {
        this.articles = articles;
    }

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

        public NewsApiArticle() {
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUrlToImage() {
            return urlToImage;
        }

        public void setUrlToImage(String urlToImage) {
            this.urlToImage = urlToImage;
        }

        public String getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(String publishedAt) {
            this.publishedAt = publishedAt;
        }

        public NewsApiSource getSource() {
            return source;
        }

        public void setSource(NewsApiSource source) {
            this.source = source;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class NewsApiSource {

        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        public NewsApiSource() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
