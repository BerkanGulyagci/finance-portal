package com.finance.portal.market.application.futures;

import com.finance.portal.market.application.stock.StockQueryService;
import com.finance.portal.market.application.stock.StockSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class FuturesQueryService {

    private static final Logger logger = LoggerFactory.getLogger(FuturesQueryService.class);

    private final StockQueryService stockQueryService;
    private final FuturesSymbolProvider futuresSymbolProvider;

    public FuturesQueryService(StockQueryService stockQueryService, FuturesSymbolProvider futuresSymbolProvider) {
        this.stockQueryService = stockQueryService;
        this.futuresSymbolProvider = futuresSymbolProvider;
    }

    @Cacheable(
            cacheNames = "market.futures.page",
            key = "'page:' + #page + ':size:' + #size"
    )
    public FuturesPageResponse getPagedFutures(int page, int size) {
        int totalElements = futuresSymbolProvider.getTotalElements();
        List<String> symbols = futuresSymbolProvider.getPagedSymbols(page, size);

        List<StockSummary> content;

        if (symbols.isEmpty()) {
            content = List.of();
        } else {
            // Sequential with delay to avoid Yahoo rate limiting
            content = new java.util.ArrayList<>();
            for (String symbol : symbols) {
                try {
                    StockSummary s = stockQueryService.getStockSummary(symbol);
                    if (s != null) content.add(s);
                    Thread.sleep(80);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ex) {
                    logger.warn("Failed to fetch futures summary for symbol {}: {}", symbol, ex.getMessage());
                }
            }

            if (content.isEmpty()) {
                logger.warn("No futures summaries could be fetched for page {} and size {}", page, size);
            }
        }

        int totalPages = size > 0
                ? (int) Math.ceil((double) totalElements / size)
                : 0;

        FuturesPageResponse response = new FuturesPageResponse();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);

        return response;
    }
}

