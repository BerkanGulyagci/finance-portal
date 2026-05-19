package com.finance.portal.market.application.ipo;

import com.finance.portal.market.application.ipo.port.IpoCalendarPort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IpoService {

    private final IpoCalendarPort ipoCalendarPort;

    public IpoService(IpoCalendarPort ipoCalendarPort) {
        this.ipoCalendarPort = ipoCalendarPort;
    }

    @Cacheable(cacheNames = "market.ipo", key = "'list'")
    public List<IpoItem> getIpos() {
        return ipoCalendarPort.fetchIpos();
    }
}
