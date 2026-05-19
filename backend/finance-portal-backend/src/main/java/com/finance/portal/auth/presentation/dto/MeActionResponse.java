package com.finance.portal.auth.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MeActionResponse {

    private final boolean requiresReLogin;
}
