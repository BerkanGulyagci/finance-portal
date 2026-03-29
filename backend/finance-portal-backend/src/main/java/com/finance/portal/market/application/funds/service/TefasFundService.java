package com.finance.portal.market.application.funds.service;

import com.finance.portal.market.application.funds.model.TefasFundItem;
import com.finance.portal.market.application.funds.model.TefasFundPageResponse;
import com.finance.portal.market.infrastructure.external.tefas.TefasFundClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TefasFundService {

    private static final Logger log = LoggerFactory.getLogger(TefasFundService.class);

    private final TefasFundClient tefasFundClient;

    public TefasFundService(TefasFundClient tefasFundClient) {
        this.tefasFundClient = tefasFundClient;
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
