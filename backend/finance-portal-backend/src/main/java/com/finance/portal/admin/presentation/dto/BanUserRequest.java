package com.finance.portal.admin.presentation.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BanUserRequest {

    private BanType banType;
    private Integer durationValue;
    private DurationUnit durationUnit;
    /** Admin'in yazdığı ban sebebi (opsiyonel; kullanıcıya e-postada gösterilir, admin listesinde saklanır). */
    private String reason;
}
