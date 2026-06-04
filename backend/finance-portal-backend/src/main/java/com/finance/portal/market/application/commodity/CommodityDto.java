package com.finance.portal.market.application.commodity;

import lombok.Data;

/**
 * /api/v1/commodities/list endpoint'i için özet DTO.
 */
@Data
public class CommodityDto {

    private String  symbol;
    private String  displayNameTr;
    private String  displayNameEn;
    private String  category;
    private String  categoryDisplayTr;
    private String  unit;
    private boolean needsCentConversion;
    private boolean enabled;
    private String  source;
    private String  dataType;
}
