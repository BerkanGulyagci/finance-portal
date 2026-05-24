package com.finance.portal.alarm.presentation.mapper;

import com.finance.portal.alarm.domain.Alarm;
import com.finance.portal.alarm.domain.AlarmCurrency;
import com.finance.portal.alarm.presentation.dto.AlarmResponse;

public final class AlarmMapper {

    private AlarmMapper() {
    }

    public static AlarmResponse toResponse(Alarm a) {
        return new AlarmResponse(
                a.getId(),
                a.getAssetType() != null ? a.getAssetType().name() : null,
                a.getSymbol(),
                a.getInstrumentName(),
                a.getMetric() != null ? a.getMetric().name() : null,
                a.getDirection() != null ? a.getDirection().name() : null,
                a.getThreshold(),
                a.getFrequency() != null ? a.getFrequency().name() : null,
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getLastObservedValue(),
                AlarmCurrency.symbolFor(a.getAssetType(), a.getSymbol()),
                a.getNote(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getTriggeredAt(),
                a.getLastTriggeredAt()
        );
    }
}
