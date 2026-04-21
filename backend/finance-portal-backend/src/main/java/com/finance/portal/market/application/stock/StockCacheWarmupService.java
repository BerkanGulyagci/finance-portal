package com.finance.portal.market.application.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StockCacheWarmupService {

    private static final Logger log = LoggerFactory.getLogger(StockCacheWarmupService.class);

    private final StockQueryService stockQueryService;
    private final StockSymbolProvider stockSymbolProvider;

    public StockCacheWarmupService(StockQueryService stockQueryService, StockSymbolProvider stockSymbolProvider) {
        this.stockQueryService = stockQueryService;
        this.stockSymbolProvider = stockSymbolProvider;
    }

    // Uygulama başladığında tüm sayfaları cache'e yükle
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupOnStartup() {
        log.info("Stock cache warmup starting...");
        warmupAllPages();
    }

    // Her 8 dakikada bir yenile (TTL 10 dakika, biraz önce yenile)
    @Scheduled(fixedDelay = 8 * 60 * 1000, initialDelay = 10 * 60 * 1000)
    public void scheduledWarmup() {
        log.info("Stock cache scheduled refresh starting...");
        warmupAllPages();
    }

    private void warmupAllPages() {
        int total = stockSymbolProvider.getTotalElements();
        int pageSize = 20;
        int totalPages = (int) Math.ceil((double) total / pageSize);

        for (int page = 0; page < totalPages; page++) {
            try {
                stockQueryService.getPagedStockSummaries(page, pageSize);
                log.info("Stock cache warmup: page {}/{} done", page + 1, totalPages);
            } catch (Exception e) {
                log.warn("Stock cache warmup failed for page {}: {}", page, e.getMessage());
            }
        }
        log.info("Stock cache warmup complete. {} pages cached.", totalPages);
    }
}
