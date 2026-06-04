package com.finance.portal.market.infrastructure.external.yahoo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class YahooChartResponseDto {

    private Chart chart;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chart {

        private List<Result> result;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private Meta meta;

        private List<Long> timestamp;

        private Indicators indicators;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Indicators {

        private List<Quote> quote;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {

        private List<BigDecimal> open;
        private List<BigDecimal> high;
        private List<BigDecimal> low;
        private List<BigDecimal> close;
        private List<Long> volume;
    }

    @Getter
    @Setter
    @NoArgsConstructor
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
    }
}
