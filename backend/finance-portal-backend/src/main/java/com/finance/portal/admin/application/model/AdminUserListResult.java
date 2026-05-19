package com.finance.portal.admin.application.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdminUserListResult {

    private final List<AdminUserView> users;
    private final boolean hasMore;
}
