package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.market.application.fx.model.OpenFxFeed;
import com.finance.portal.market.application.fx.port.OpenFxPort;
import com.finance.portal.market.infrastructure.external.fx.dto.OpenErApiResponseDto;
import org.springframework.stereotype.Component;

@Component
public class OpenFxAdapter implements OpenFxPort {

    private final OpenFxClient openFxClient;

    public OpenFxAdapter(OpenFxClient openFxClient) {
        this.openFxClient = openFxClient;
    }

    @Override
    public OpenFxFeed fetchLatestRates(String base) {
        OpenErApiResponseDto dto = openFxClient.fetchLatestRates(base);
        return new OpenFxFeed(dto.getBaseCode(), dto.getTimeLastUpdateUtc(), dto.getRates());
    }
}
