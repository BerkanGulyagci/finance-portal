package com.finance.portal.market.infrastructure.external.yahoo;

import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooQuoteSeries;
import com.finance.portal.market.application.stock.model.YahooStockMeta;

final class YahooChartSnapshotMapper {

    private YahooChartSnapshotMapper() {
    }

    static YahooChartSnapshot fromDto(YahooChartResponseDto dto) {
        YahooChartSnapshot snapshot = new YahooChartSnapshot();
        if (dto == null
                || dto.getChart() == null
                || dto.getChart().getResult() == null
                || dto.getChart().getResult().isEmpty()) {
            return snapshot;
        }

        YahooChartResponseDto.Result result = dto.getChart().getResult().get(0);
        snapshot.setMeta(mapMeta(result.getMeta()));
        snapshot.setTimestamps(result.getTimestamp());

        if (result.getIndicators() != null
                && result.getIndicators().getQuote() != null
                && !result.getIndicators().getQuote().isEmpty()) {
            YahooChartResponseDto.Quote q = result.getIndicators().getQuote().get(0);
            YahooQuoteSeries quote = new YahooQuoteSeries();
            quote.setOpen(q.getOpen());
            quote.setHigh(q.getHigh());
            quote.setLow(q.getLow());
            quote.setClose(q.getClose());
            quote.setVolume(q.getVolume());
            snapshot.setQuote(quote);
        }

        return snapshot;
    }

    private static YahooStockMeta mapMeta(YahooChartResponseDto.Meta meta) {
        if (meta == null) {
            return null;
        }
        YahooStockMeta m = new YahooStockMeta();
        m.setSymbol(meta.getSymbol());
        m.setLongName(meta.getLongName());
        m.setCurrency(meta.getCurrency());
        m.setExchangeName(meta.getExchangeName());
        m.setRegularMarketPrice(meta.getRegularMarketPrice());
        m.setPreviousClose(meta.getPreviousClose());
        m.setRegularMarketDayHigh(meta.getRegularMarketDayHigh());
        m.setRegularMarketDayLow(meta.getRegularMarketDayLow());
        m.setRegularMarketVolume(meta.getRegularMarketVolume());
        m.setFiftyTwoWeekHigh(meta.getFiftyTwoWeekHigh());
        m.setFiftyTwoWeekLow(meta.getFiftyTwoWeekLow());
        m.setRegularMarketTime(meta.getRegularMarketTime());
        return m;
    }
}
