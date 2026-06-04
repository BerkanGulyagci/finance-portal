package com.finance.portal.market.application.viop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ViopContract implements Serializable {

    // Akbank'tan gelen ana alanlar (güvenilir)
    private String name;
    private String changePercent;
    private String lastPrice;
    private String high;
    private String low;
    private String openPositionCount;
    private String openPositionChange;
    private String settlementPrice;
    private String prevSettlementPrice;
    private String time;
}
