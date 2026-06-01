package com.finance.portal.market.infrastructure.external.yahoo;

import com.finance.portal.market.application.stock.model.YahooChartSnapshot;
import com.finance.portal.market.application.stock.model.YahooStockMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * YahooChartSnapshotMapper saf dönüşüm testleri: Yahoo chart DTO → YahooChartSnapshot.
 * Mapper paket-özel olduğu için test aynı pakette.
 */
class YahooChartSnapshotMapperTest {

    private YahooChartResponseDto.Meta fullMeta() {
        YahooChartResponseDto.Meta meta = new YahooChartResponseDto.Meta();
        meta.setSymbol("THYAO.IS");
        meta.setLongName("Türk Hava Yolları");
        meta.setCurrency("TRY");
        meta.setExchangeName("IST");
        meta.setRegularMarketPrice(new BigDecimal("280.50"));
        meta.setPreviousClose(new BigDecimal("275.00"));
        meta.setRegularMarketDayHigh(new BigDecimal("282.00"));
        meta.setRegularMarketDayLow(new BigDecimal("274.10"));
        meta.setRegularMarketVolume(123456L);
        meta.setFiftyTwoWeekHigh(new BigDecimal("320.00"));
        meta.setFiftyTwoWeekLow(new BigDecimal("150.00"));
        meta.setRegularMarketTime(1717142400L);
        return meta;
    }

    private YahooChartResponseDto wrap(YahooChartResponseDto.Result result) {
        YahooChartResponseDto.Chart chart = new YahooChartResponseDto.Chart();
        chart.setResult(List.of(result));
        YahooChartResponseDto dto = new YahooChartResponseDto();
        dto.setChart(chart);
        return dto;
    }

    @Test
    @DisplayName("fromDto: meta, timestamps ve quote serilerini tam map eder")
    void fromDto_mapsAllFields() {
        YahooChartResponseDto.Quote q = new YahooChartResponseDto.Quote();
        q.setOpen(List.of(new BigDecimal("1.1"), new BigDecimal("2.2")));
        q.setHigh(List.of(new BigDecimal("3.3"), new BigDecimal("4.4")));
        q.setLow(List.of(new BigDecimal("0.5"), new BigDecimal("0.6")));
        q.setClose(List.of(new BigDecimal("2.0"), new BigDecimal("2.1")));
        q.setVolume(List.of(100L, 200L));

        YahooChartResponseDto.Indicators indicators = new YahooChartResponseDto.Indicators();
        indicators.setQuote(List.of(q));

        YahooChartResponseDto.Result result = new YahooChartResponseDto.Result();
        result.setMeta(fullMeta());
        result.setTimestamp(List.of(1000L, 2000L, 3000L));
        result.setIndicators(indicators);

        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(wrap(result));

        // meta
        assertThat(snapshot.hasMeta()).isTrue();
        YahooStockMeta meta = snapshot.getMeta();
        assertThat(meta.getSymbol()).isEqualTo("THYAO.IS");
        assertThat(meta.getLongName()).isEqualTo("Türk Hava Yolları");
        assertThat(meta.getCurrency()).isEqualTo("TRY");
        assertThat(meta.getExchangeName()).isEqualTo("IST");
        assertThat(meta.getRegularMarketPrice()).isEqualByComparingTo("280.50");
        assertThat(meta.getPreviousClose()).isEqualByComparingTo("275.00");
        assertThat(meta.getRegularMarketDayHigh()).isEqualByComparingTo("282.00");
        assertThat(meta.getRegularMarketDayLow()).isEqualByComparingTo("274.10");
        assertThat(meta.getRegularMarketVolume()).isEqualTo(123456L);
        assertThat(meta.getFiftyTwoWeekHigh()).isEqualByComparingTo("320.00");
        assertThat(meta.getFiftyTwoWeekLow()).isEqualByComparingTo("150.00");
        assertThat(meta.getRegularMarketTime()).isEqualTo(1717142400L);

        // timestamps
        assertThat(snapshot.getTimestamps()).containsExactly(1000L, 2000L, 3000L);

        // quote
        assertThat(snapshot.getQuote()).isNotNull();
        assertThat(snapshot.getQuote().getOpen())
                .containsExactly(new BigDecimal("1.1"), new BigDecimal("2.2"));
        assertThat(snapshot.getQuote().getHigh())
                .containsExactly(new BigDecimal("3.3"), new BigDecimal("4.4"));
        assertThat(snapshot.getQuote().getLow())
                .containsExactly(new BigDecimal("0.5"), new BigDecimal("0.6"));
        assertThat(snapshot.getQuote().getClose())
                .containsExactly(new BigDecimal("2.0"), new BigDecimal("2.1"));
        assertThat(snapshot.getQuote().getVolume()).containsExactly(100L, 200L);
    }

    @Test
    @DisplayName("fromDto null → boş snapshot (meta/timestamps/quote null)")
    void fromDto_null_returnsEmptySnapshot() {
        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(null);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.hasMeta()).isFalse();
        assertThat(snapshot.getMeta()).isNull();
        assertThat(snapshot.getTimestamps()).isNull();
        assertThat(snapshot.getQuote()).isNull();
    }

    @Test
    @DisplayName("fromDto chart null → boş snapshot")
    void fromDto_chartNull_returnsEmptySnapshot() {
        YahooChartResponseDto dto = new YahooChartResponseDto();

        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(dto);

        assertThat(snapshot.hasMeta()).isFalse();
        assertThat(snapshot.getTimestamps()).isNull();
        assertThat(snapshot.getQuote()).isNull();
    }

    @Test
    @DisplayName("fromDto result listesi boş → boş snapshot")
    void fromDto_emptyResult_returnsEmptySnapshot() {
        YahooChartResponseDto.Chart chart = new YahooChartResponseDto.Chart();
        chart.setResult(List.of());
        YahooChartResponseDto dto = new YahooChartResponseDto();
        dto.setChart(chart);

        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(dto);

        assertThat(snapshot.hasMeta()).isFalse();
        assertThat(snapshot.getTimestamps()).isNull();
        assertThat(snapshot.getQuote()).isNull();
    }

    @Test
    @DisplayName("fromDto meta null → snapshot.meta null ama timestamps map edilir")
    void fromDto_nullMeta() {
        YahooChartResponseDto.Result result = new YahooChartResponseDto.Result();
        result.setMeta(null);
        result.setTimestamp(List.of(10L, 20L));

        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(wrap(result));

        assertThat(snapshot.getMeta()).isNull();
        assertThat(snapshot.hasMeta()).isFalse();
        assertThat(snapshot.getTimestamps()).containsExactly(10L, 20L);
        assertThat(snapshot.getQuote()).isNull();
    }

    @Test
    @DisplayName("fromDto indicators null → quote null, meta map edilir")
    void fromDto_nullIndicators() {
        YahooChartResponseDto.Result result = new YahooChartResponseDto.Result();
        result.setMeta(fullMeta());
        result.setTimestamp(List.of(1L));
        result.setIndicators(null);

        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(wrap(result));

        assertThat(snapshot.getMeta()).isNotNull();
        assertThat(snapshot.getQuote()).isNull();
    }

    @Test
    @DisplayName("fromDto quote listesi boş → snapshot.quote null")
    void fromDto_emptyQuoteList() {
        YahooChartResponseDto.Indicators indicators = new YahooChartResponseDto.Indicators();
        indicators.setQuote(List.of());

        YahooChartResponseDto.Result result = new YahooChartResponseDto.Result();
        result.setMeta(fullMeta());
        result.setIndicators(indicators);

        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(wrap(result));

        assertThat(snapshot.getQuote()).isNull();
    }

    @Test
    @DisplayName("fromDto quote serileri null → quote oluşur ama serileri null")
    void fromDto_quoteWithNullSeries() {
        YahooChartResponseDto.Quote q = new YahooChartResponseDto.Quote();
        // tüm seriler null
        YahooChartResponseDto.Indicators indicators = new YahooChartResponseDto.Indicators();
        indicators.setQuote(List.of(q));

        YahooChartResponseDto.Result result = new YahooChartResponseDto.Result();
        result.setIndicators(indicators);

        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(wrap(result));

        assertThat(snapshot.getQuote()).isNotNull();
        assertThat(snapshot.getQuote().getOpen()).isNull();
        assertThat(snapshot.getQuote().getHigh()).isNull();
        assertThat(snapshot.getQuote().getLow()).isNull();
        assertThat(snapshot.getQuote().getClose()).isNull();
        assertThat(snapshot.getQuote().getVolume()).isNull();
    }

    @Test
    @DisplayName("fromDto meta tüm alanları null → mapMeta null alanları korur")
    void fromDto_metaWithNullFields() {
        YahooChartResponseDto.Meta meta = new YahooChartResponseDto.Meta();
        YahooChartResponseDto.Result result = new YahooChartResponseDto.Result();
        result.setMeta(meta);

        YahooChartSnapshot snapshot = YahooChartSnapshotMapper.fromDto(wrap(result));

        assertThat(snapshot.hasMeta()).isTrue();
        YahooStockMeta m = snapshot.getMeta();
        assertThat(m.getSymbol()).isNull();
        assertThat(m.getLongName()).isNull();
        assertThat(m.getCurrency()).isNull();
        assertThat(m.getExchangeName()).isNull();
        assertThat(m.getRegularMarketPrice()).isNull();
        assertThat(m.getPreviousClose()).isNull();
        assertThat(m.getRegularMarketDayHigh()).isNull();
        assertThat(m.getRegularMarketDayLow()).isNull();
        assertThat(m.getRegularMarketVolume()).isNull();
        assertThat(m.getFiftyTwoWeekHigh()).isNull();
        assertThat(m.getFiftyTwoWeekLow()).isNull();
        assertThat(m.getRegularMarketTime()).isNull();
    }
}
