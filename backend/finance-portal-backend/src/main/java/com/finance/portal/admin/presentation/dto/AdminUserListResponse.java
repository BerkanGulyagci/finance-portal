package com.finance.portal.admin.presentation.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AdminUserListResponse {

    private List<AdminUserResponse> users;
    private int first;
    private int max;
}
