package com.finance.portal.market.application.ipo;

import com.finance.portal.market.infrastructure.external.ipo.IpoCalendarClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IpoService {

    private final IpoCalendarClient ipoCalendarClient;

    public IpoService(IpoCalendarClient ipoCalendarClient) {
        this.ipoCalendarClient = ipoCalendarClient;
    }

    @Cacheable(cacheNames = "market.ipo", key = "'list'")
    public List<IpoItem> getIpos() {
        return ipoCalendarClient.fetchIpos();
    }
}
