package com.finance.portal.news.application.scheduler;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.news.application.service.NewsAggregateCache;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NewsCacheScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NewsCacheScheduler.class);

    private final NewsAggregateCache newsAggregateCache;
    private final CentralIntegrationLogService integrationLogService;

    public NewsCacheScheduler(NewsAggregateCache newsAggregateCache,
                              CentralIntegrationLogService integrationLogService) {
        this.newsAggregateCache = newsAggregateCache;
        this.integrationLogService = integrationLogService;
    }

    @Scheduled(fixedDelayString = "${news.cache.warmup.fixed-delay-ms}")
    @SchedulerLock(name = "news-cache-warmup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5M")
    public void warmUpNewsCache() {
        // Çoklu-kaynak toplu haber listesini tazele (ücretsiz API kotalarını koruyan ana yenileme noktası).
        // NewsAPI dahil tüm sağlayıcılar buradan (NewsAggregateCache) çekilir.
        try {
            newsAggregateCache.refresh();
            logger.debug("News cache warm-up completed successfully");
        } catch (Exception e) {
            logger.warn("News aggregate refresh failed: {}", e.getMessage());
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
