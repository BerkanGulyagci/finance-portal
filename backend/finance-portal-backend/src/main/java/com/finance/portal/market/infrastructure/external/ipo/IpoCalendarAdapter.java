package com.finance.portal.market.infrastructure.external.ipo;

import com.finance.portal.market.application.ipo.IpoItem;
import com.finance.portal.market.application.ipo.port.IpoCalendarPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IpoCalendarAdapter implements IpoCalendarPort {

    private final IpoCalendarClient ipoCalendarClient;

    public IpoCalendarAdapter(IpoCalendarClient ipoCalendarClient) {
        this.ipoCalendarClient = ipoCalendarClient;
    }

    @Override
    public List<IpoItem> fetchIpos() {
        return ipoCalendarClient.fetchIpos();
    }
}
