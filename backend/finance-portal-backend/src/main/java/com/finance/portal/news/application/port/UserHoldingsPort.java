package com.finance.portal.news.application.port;

import java.util.List;

/**
 * Kullanıcının portföylerindeki enstrümanlar (kişiselleştirilmiş haber eşleştirmesi için).
 * News modülünü portföy modülünün iç detaylarından ayırır (adapter PortfolioService'i çağırır).
 */
public interface UserHoldingsPort {

    /** Kullanıcının tuttuğu varlıklar; portföy yoksa boş liste. */
    List<Holding> holdingsForUser(String userId);

    /** Tek bir varlık: sembol (THYAO/BTC), görünen ad (Türk Hava Yolları/Bitcoin), varlık türü (AssetType adı). */
    record Holding(String symbol, String name, String assetType) {
    }
}
