package com.finance.portal.market.application.ipo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
public class IpoItem implements Serializable {

    private String ticker;
    private String name;
    private String date;
    private String url;
}
