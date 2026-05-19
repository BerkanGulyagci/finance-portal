package com.finance.portal.market.application.crypto.port;

import com.finance.portal.market.application.crypto.model.CryptoChartCandle;

import java.util.List;

public interface BinanceKlinePort {

    /**
     * Binance Spot klines — tek sayfa (en fazla 1000 mum).
     *
     * @return boş liste: sembol yok / hata (exception fırlatılmaz)
     */
    List<CryptoChartCandle> fetchKlinesPage(String binanceSymbol, String interval, int limit, long startTimeMs);
}
