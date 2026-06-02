package com.finance.portal.market.application.stock;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.index.IndexQueryService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StockCacheWarmupService {

    private static final Logger log = LoggerFactory.getLogger(StockCacheWarmupService.class);

    private final StockQueryService stockQueryService;
    private final StockSymbolProvider stockSymbolProvider;
    private final IndexQueryService indexQueryService;
    private final CentralIntegrationLogService integrationLogService;

    public StockCacheWarmupService(StockQueryService stockQueryService,
                                   StockSymbolProvider stockSymbolProvider,
                                   IndexQueryService indexQueryService,
                                   CentralIntegrationLogService integrationLogService) {
        this.stockQueryService = stockQueryService;
        this.stockSymbolProvider = stockSymbolProvider;
        this.indexQueryService = indexQueryService;
        this.integrationLogService = integrationLogService;
    }

    // Uygulama başladığında tüm sayfaları cache'e yükle
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupOnStartup() {
        log.info("Stock cache warmup starting...");
        warmupAllPages();
        warmupIndices();
    }

    // Her 8 dakikada bir yenile (TTL 10 dakika, biraz önce yenile)
    @Scheduled(fixedDelay = 8 * 60 * 1000, initialDelay = 10 * 60 * 1000)
    @SchedulerLock(name = "stock-cache-warmup", lockAtMostFor = "PT8M", lockAtLeastFor = "PT7M")
    public void scheduledWarmup() {
        log.info("Stock cache scheduled refresh starting...");
        warmupAllPages();
        warmupIndices();
    }

    /** BIST endeks listesini (~42 endeks → Yahoo snapshot, ~45 çağrı) önceden ısıt — istek yolunda 45 çağrı olmasın. */
    private void warmupIndices() {
        try {
            int count = indexQueryService.getIndices().size();
            log.info("BIST index list cache warmup done ({} indices)", count);
        } catch (Exception e) {
            log.warn("BIST index list cache warmup failed: {}", e.getMessage());
        }
    }

    private void warmupAllPages() {
        int total = stockSymbolProvider.getTotalElements();
        int pageSize = 20;
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int failedCount = 0;

        for (int page = 0; page < totalPages; page++) {
            try {
                stockQueryService.getPagedStockSummaries(page, pageSize);
                log.info("Stock cache warmup: page {}/{} done", page + 1, totalPages);
            } catch (Exception e) {
                failedCount++;
                log.warn("Stock cache warmup failed for page {}: {}", page, e.getMessage());
            }
        }
        log.info("Stock cache warmup complete. {} pages cached.", totalPages);

        if (failedCount > 0) {
            String level = failedCount >= totalPages ? "ERROR" : "WARN";
            integrationLogService.publish(
                    IntegrationLogSupport.EVENT_SCHEDULER_JOB_FAILED,
                    level,
                    "Stock cache warmup completed with failures",
                    IntegrationLogSupport.PROVIDER_YAHOO,
                    "stock_cache_warmup",
                    null,
                    null,
                    null,
                    failedCount < totalPages,
                    Map.of(
                            "jobName", "stock_cache_warmup",
                            "failedCount", failedCount,
                            "totalCount", totalPages,
                            "trigger", IntegrationLogSupport.TRIGGER_SCHEDULER
                    ),
                    StockCacheWarmupService.class.getName()
            );
        }
    }
}
