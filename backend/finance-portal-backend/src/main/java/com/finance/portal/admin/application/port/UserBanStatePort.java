package com.finance.portal.admin.application.port;

import com.finance.portal.admin.application.model.UserBanState;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserBanStatePort {

    void save(UserBanState state);

    void deleteByUserId(String userId);

    Optional<UserBanState> findByUserId(String userId);

    Map<String, UserBanState> findByUserIds(Collection<String> userIds);

    List<UserBanState> findExpiredTemporaryBans(Instant now);
}
