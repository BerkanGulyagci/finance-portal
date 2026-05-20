package com.finance.portal.news.application.scheduler;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.news.application.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NewsCacheScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NewsCacheScheduler.class);

    private final NewsService newsService;
    private final CentralIntegrationLogService integrationLogService;

    public NewsCacheScheduler(NewsService newsService,
                              CentralIntegrationLogService integrationLogService) {
        this.newsService = newsService;
        this.integrationLogService = integrationLogService;
    }

    @Scheduled(fixedDelayString = "${news.cache.warmup.fixed-delay-ms}")
    public void warmUpNewsCache() {
        try {
            newsService.getNews("business", "tr", 1, 10, null);
            logger.debug("News cache warm-up completed successfully");
        } catch (ExternalApiException e) {
            logger.warn("News cache warm-up failed (external API error): {}", e.getMessage());
            integrationLogService.publish(
                    IntegrationLogSupport.EVENT_SCHEDULER_JOB_FAILED,
                    "ERROR",
                    "News cache warm-up failed",
                    IntegrationLogSupport.PROVIDER_NEWSAPI,
                    "news_cache_warmup",
                    "503",
                    null,
                    null,
                    null,
                    Map.of(
                            "jobName", "news_cache_warmup",
                            "failedCount", 1,
                            "totalCount", 1,
                            "trigger", IntegrationLogSupport.TRIGGER_SCHEDULER
                    ),
                    NewsCacheScheduler.class.getName()
            );
        }
    }
}
