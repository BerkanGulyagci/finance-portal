package com.finance.portal.market.application.fx.port;

import com.finance.portal.market.application.fx.model.FxHistoryPoint;

import java.time.LocalDate;
import java.util.List;

public interface TcmbFxHistoryPort {

    List<FxHistoryPoint> fetchHistory(String currencyCode, LocalDate from, LocalDate to);
}
