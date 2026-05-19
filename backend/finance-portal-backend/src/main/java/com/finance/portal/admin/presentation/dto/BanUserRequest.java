package com.finance.portal.admin.presentation.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BanUserRequest {

    private BanType banType;
    private Integer durationValue;
    private DurationUnit durationUnit;
}
