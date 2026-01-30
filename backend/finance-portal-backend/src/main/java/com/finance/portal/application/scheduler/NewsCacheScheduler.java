package com.finance.portal.application.scheduler;

import com.finance.portal.application.service.NewsService;
import com.finance.portal.infrastructure.exception.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NewsCacheScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NewsCacheScheduler.class);

    private final NewsService newsService;

    public NewsCacheScheduler(NewsService newsService) {
        this.newsService = newsService;
    }

    @Scheduled(fixedDelayString = "${news.cache.warmup.fixed-delay-ms}")
    public void warmUpNewsCache() {
        try {
            newsService.getNews();
            logger.debug("News cache warm-up completed successfully");
        } catch (ExternalApiException e) {
            logger.warn("News cache warm-up failed (external API error): {}", e.getMessage());
        }
    }
}
