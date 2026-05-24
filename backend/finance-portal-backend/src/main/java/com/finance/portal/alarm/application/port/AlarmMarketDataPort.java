package com.finance.portal.alarm.application.port;

import com.finance.portal.alarm.application.model.AlarmMarketSnapshot;
import com.finance.portal.common.domain.AssetType;

/**
 * Alarm değerlendirmesi için bir enstrümanın anlık metriklerini sağlayan port.
 * Tüm 8 varlık tipi için fiyat; tip destekliyorsa değişim%/hacim döner.
 */
public interface AlarmMarketDataPort {

    /**
     * @return anlık snapshot; veri çekilemezse {@code null} (alarm o tur atlanır)
     */
    AlarmMarketSnapshot probe(AssetType assetType, String symbol);
}
