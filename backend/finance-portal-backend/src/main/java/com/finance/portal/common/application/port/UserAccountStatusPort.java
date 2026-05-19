package com.finance.portal.common.application.port;

public interface UserAccountStatusPort {

    boolean isAccountEnabled(String userId);

    void evictAccountStatus(String userId);
}
