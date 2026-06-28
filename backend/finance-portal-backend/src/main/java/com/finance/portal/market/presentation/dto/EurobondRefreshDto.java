package com.finance.portal.market.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EurobondRefreshDto {

    private int count;
    private String xlsxUrl;
    private boolean force;
}
