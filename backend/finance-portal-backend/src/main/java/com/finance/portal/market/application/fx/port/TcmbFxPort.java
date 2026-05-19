package com.finance.portal.market.application.fx.port;

import com.finance.portal.market.application.fx.model.TcmbFxFeed;

public interface TcmbFxPort {

    TcmbFxFeed fetchLatestRates();
}
