package com.finance.portal.market.application;

import com.finance.portal.common.domain.AssetType;

public interface AssetPriceQueryService {

    /**
     * Verilen assetType ve symbol için anlık fiyat bilgisini döndürür.
     *
     * @throws com.finance.portal.common.application.exception.ResourceNotFoundException symbol bulunamazsa
     * @throws UnsupportedOperationException desteklenmeyen assetType için
     */
    AssetPriceSnapshot getCurrentPrice(AssetType assetType, String symbol);
}
