package com.finance.portal.market.infrastructure.external.yahoo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class YahooChartResponseDto {

    private Chart chart;

    public YahooChartResponseDto() {
    }

    public Chart getChart() {
        return chart;
    }

    public void setChart(Chart chart) {
        this.chart = chart;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chart {

        private List<Result> result;

        public Chart() {
        }

        public List<Result> getResult() {
            return result;
        }

        public void setResult(List<Result> result) {
            this.result = result;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private Meta meta;

        private List<Long> timestamp;

        private Indicators indicators;

        public Result() {
        }

        public Meta getMeta() {
            return meta;
        }

        public void setMeta(Meta meta) {
            this.meta = meta;
        }

        public List<Long> getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(List<Long> timestamp) {
            this.timestamp = timestamp;
        }

        public Indicators getIndicators() {
            return indicators;
        }

        public void setIndicators(Indicators indicators) {
            this.indicators = indicators;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Indicators {

        private List<Quote> quote;

        public Indicators() {
        }

        public List<Quote> getQuote() {
            return quote;
        }

        public void setQuote(List<Quote> quote) {
            this.quote = quote;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {

        private List<BigDecimal> open;
        private List<BigDecimal> high;
        private List<BigDecimal> low;
        private List<BigDecimal> close;
        private List<Long> volume;

        public Quote() {
        }

        public List<BigDecimal> getOpen() {
            return open;
        }

        public void setOpen(List<BigDecimal> open) {
            this.open = open;
        }

        public List<BigDecimal> getHigh() {
            return high;
        }

        public void setHigh(List<BigDecimal> high) {
            this.high = high;
        }

        public List<BigDecimal> getLow() {
            return low;
        }

        public void setLow(List<BigDecimal> low) {
            this.low = low;
        }

        public List<BigDecimal> getClose() {
            return close;
        }

        public void setClose(List<BigDecimal> close) {
            this.close = close;
        }

        public List<Long> getVolume() {
            return volume;
        }

        public void setVolume(List<Long> volume) {
            this.volume = volume;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {

        private String symbol;

        @JsonProperty("longName")
        private String longName;

        private String currency;

        @JsonProperty("exchangeName")
        private String exchangeName;

        @JsonProperty("regularMarketPrice")
        private BigDecimal regularMarketPrice;

        @JsonProperty("previousClose")
        private BigDecimal previousClose;

        @JsonProperty("regularMarketDayHigh")
        private BigDecimal regularMarketDayHigh;

        @JsonProperty("regularMarketDayLow")
        private BigDecimal regularMarketDayLow;

        @JsonProperty("regularMarketVolume")
        private Long regularMarketVolume;

        @JsonProperty("fiftyTwoWeekHigh")
        private BigDecimal fiftyTwoWeekHigh;

        @JsonProperty("fiftyTwoWeekLow")
        private BigDecimal fiftyTwoWeekLow;

        @JsonProperty("regularMarketTime")
        private Long regularMarketTime;

        public Meta() {
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getLongName() {
            return longName;
        }

        public void setLongName(String longName) {
            this.longName = longName;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getExchangeName() {
            return exchangeName;
        }

        public void setExchangeName(String exchangeName) {
            this.exchangeName = exchangeName;
        }

        public BigDecimal getRegularMarketPrice() {
            return regularMarketPrice;
        }

        public void setRegularMarketPrice(BigDecimal regularMarketPrice) {
            this.regularMarketPrice = regularMarketPrice;
        }

        public BigDecimal getPreviousClose() {
            return previousClose;
        }

        public void setPreviousClose(BigDecimal previousClose) {
            this.previousClose = previousClose;
        }

        public BigDecimal getRegularMarketDayHigh() {
            return regularMarketDayHigh;
        }

        public void setRegularMarketDayHigh(BigDecimal regularMarketDayHigh) {
            this.regularMarketDayHigh = regularMarketDayHigh;
        }

        public BigDecimal getRegularMarketDayLow() {
            return regularMarketDayLow;
        }

        public void setRegularMarketDayLow(BigDecimal regularMarketDayLow) {
            this.regularMarketDayLow = regularMarketDayLow;
        }

        public Long getRegularMarketVolume() {
            return regularMarketVolume;
        }

        public void setRegularMarketVolume(Long regularMarketVolume) {
            this.regularMarketVolume = regularMarketVolume;
        }

        public BigDecimal getFiftyTwoWeekHigh() {
            return fiftyTwoWeekHigh;
        }

        public void setFiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) {
            this.fiftyTwoWeekHigh = fiftyTwoWeekHigh;
        }

        public BigDecimal getFiftyTwoWeekLow() {
            return fiftyTwoWeekLow;
        }

        public void setFiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) {
            this.fiftyTwoWeekLow = fiftyTwoWeekLow;
        }

        public Long getRegularMarketTime() {
            return regularMarketTime;
        }

        public void setRegularMarketTime(Long regularMarketTime) {
            this.regularMarketTime = regularMarketTime;
        }
    }
}

