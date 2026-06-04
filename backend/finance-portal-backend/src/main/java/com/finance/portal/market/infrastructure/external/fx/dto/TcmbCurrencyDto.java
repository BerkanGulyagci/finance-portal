package com.finance.portal.market.infrastructure.external.fx.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TcmbCurrencyDto {

    @JacksonXmlProperty(isAttribute = true, localName = "CurrencyCode")
    private String currencyCode;

    @JacksonXmlProperty(localName = "Unit")
    private Integer unit;

    @JacksonXmlProperty(localName = "ForexBuying")
    private String forexBuying;

    @JacksonXmlProperty(localName = "ForexSelling")
    private String forexSelling;

    @JacksonXmlProperty(localName = "BanknoteBuying")
    private String banknoteBuying;

    @JacksonXmlProperty(localName = "BanknoteSelling")
    private String banknoteSelling;
}
