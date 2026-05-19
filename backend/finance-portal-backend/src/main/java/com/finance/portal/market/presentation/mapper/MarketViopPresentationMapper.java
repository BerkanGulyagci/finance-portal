package com.finance.portal.market.presentation.mapper;

import com.finance.portal.market.application.viop.model.ViopChartPoint;
import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.market.presentation.dto.ViopChartPointDto;
import com.finance.portal.market.presentation.dto.ViopContractDetailDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MarketViopPresentationMapper {

    public ViopContractDetailDto toViopContractDetailDto(ViopContractDetail detail) {
        ViopContractDetailDto dto = new ViopContractDetailDto();
        dto.setName(detail.getName());
        dto.setSymbol(detail.getSymbol());
        dto.setLastPrice(detail.getLastPrice());
        dto.setChangePercent(detail.getChangePercent());
        dto.setHigh(detail.getHigh());
        dto.setLow(detail.getLow());
        dto.setOpenPositionCount(detail.getOpenPositionCount());
        dto.setOpenPositionChange(detail.getOpenPositionChange());
        dto.setSettlementPrice(detail.getSettlementPrice());
        dto.setPrevSettlementPrice(detail.getPrevSettlementPrice());
        dto.setTime(detail.getTime());
        dto.setTradingViewSymbol(detail.getTradingViewSymbol());
        return dto;
    }

    public List<ViopChartPointDto> toViopChartPointDtoList(List<ViopChartPoint> points) {
        return points.stream()
                .map(this::toViopChartPointDto)
                .collect(Collectors.toList());
    }

    public ViopChartPointDto toViopChartPointDto(ViopChartPoint point) {
        return new ViopChartPointDto(point.getTimestamp(), point.getDateTime(), point.getValue());
    }
}
