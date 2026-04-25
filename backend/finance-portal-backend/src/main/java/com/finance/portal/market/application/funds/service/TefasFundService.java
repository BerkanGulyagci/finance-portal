package com.finance.portal.market.application.funds.service;

import com.finance.portal.market.application.funds.model.TefasFundItem;
import com.finance.portal.market.application.funds.model.TefasFundPageResponse;
import com.finance.portal.market.infrastructure.external.tefas.TefasFundAnalysisScraper;
import com.finance.portal.market.infrastructure.external.tefas.TefasFundClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TefasFundService {

    private static final Logger log = LoggerFactory.getLogger(TefasFundService.class);

    private final TefasFundClient tefasFundClient;
    private final TefasFundAnalysisScraper analysisScraper;

    public TefasFundService(TefasFundClient tefasFundClient, TefasFundAnalysisScraper analysisScraper) {
        this.tefasFundClient = tefasFundClient;
        this.analysisScraper = analysisScraper;
    }

    /**
     * Returns all TEFAS funds for today, cached.
     * kind: YAT = mutual funds, BYF = ETFs, EMK = pension funds
     */
    @Cacheable(cacheNames = "market.tefas.funds", key = "'all:' + #kind")
    public List<TefasFundItem> getAllFunds(String kind) {
        log.info("Fetching TEFAS funds from source (kind={})", kind);
        return tefasFundClient.fetchFunds(kind);
    }

    public List<TefasFundItem> getFundByCode(String code) {
        List<TefasFundItem> items = tefasFundClient.fetchFundByCode(code);
        if (!items.isEmpty()) {
            TefasFundItem item = items.get(0);
            // Fon tipini set et
            String kind = tefasFundClient.detectKind(code);
            item.setKind(kind);
            // Dönem getirilerini scrape et
            try {
                Map<String, Double> returns = analysisScraper.fetchReturns(code);
                item.setReturn1M(returns.get("return1M"));
                item.setReturn3M(returns.get("return3M"));
                item.setReturn6M(returns.get("return6M"));
                item.setReturn1Y(returns.get("return1Y"));
                item.setDailyReturn(returns.get("dailyReturn"));
                Double rv = returns.get("riskValue");
                if (rv != null) item.setRiskValue(rv.intValue());
            } catch (Exception e) {
                log.warn("Failed to scrape returns for {}: {}", code, e.getMessage());
            }
        }
        return items;
    }

    public TefasFundPageResponse getPagedFunds(String kind, int page, int size) {
        List<TefasFundItem> all = getAllFunds(kind);

        int totalElements = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        int start = page * size;
        int end = Math.min(start + size, totalElements);

        List<TefasFundItem> content = (start >= totalElements)
                ? List.of()
                : all.subList(start, end);

        TefasFundPageResponse response = new TefasFundPageResponse();
        response.setContent(content);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(totalPages);
        return response;
    }
}
