package com.finance.portal.market.application.fx.port;

import com.finance.portal.market.application.fx.model.OpenFxFeed;

public interface OpenFxPort {

    OpenFxFeed fetchLatestRates(String base);
}
