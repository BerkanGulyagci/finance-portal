package com.finance.portal.market.presentation.mapper;

import com.finance.portal.market.application.fx.model.FxHistory;
import com.finance.portal.market.application.fx.model.FxHistoryPoint;
import com.finance.portal.market.application.fx.model.FxLatestRates;
import com.finance.portal.market.application.fx.model.FxRateItem;
import com.finance.portal.market.presentation.dto.FxHistoryResponse;
import com.finance.portal.market.presentation.dto.FxLatestResponse;
import com.finance.portal.market.presentation.dto.FxRateItemDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MarketFxPresentationMapper {

    public FxLatestResponse toFxLatestResponse(FxLatestRates rates) {
        List<FxRateItemDto> items = rates.getRates().stream()
                .map(this::toFxRateItemDto)
                .collect(Collectors.toList());
        return new FxLatestResponse(
                rates.getProvider(),
                rates.getSourceType(),
                rates.getBase(),
                rates.getAsOf(),
                items);
    }

    public FxHistoryResponse toFxHistoryResponse(FxHistory history) {
        List<com.finance.portal.market.presentation.dto.FxHistoryPoint> points = history.getPoints().stream()
                .map(this::toFxHistoryPointDto)
                .collect(Collectors.toList());
        return new FxHistoryResponse(
                history.getSymbol(),
                history.getQuoteCurrency(),
                history.getRange(),
                points);
    }

    private FxRateItemDto toFxRateItemDto(FxRateItem item) {
        return new FxRateItemDto(item.getSymbol(), item.getBuy(), item.getSell(), item.getUnit(),
                item.getEffectiveBuy(), item.getEffectiveSell());
    }

    private com.finance.portal.market.presentation.dto.FxHistoryPoint toFxHistoryPointDto(FxHistoryPoint point) {
        return new com.finance.portal.market.presentation.dto.FxHistoryPoint(point.getDate(), point.getClose());
    }
}
