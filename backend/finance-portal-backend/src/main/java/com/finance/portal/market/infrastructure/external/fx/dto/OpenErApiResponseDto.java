package com.finance.portal.market.infrastructure.external.fx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class OpenErApiResponseDto {

    private String result;

    @JsonProperty("base_code")
    private String baseCode;

    @JsonProperty("time_last_update_utc")
    private String timeLastUpdateUtc;

    @JsonProperty("rates")
    private Map<String, Double> rates;
}
