package com.finance.portal.admin.infrastructure.persistence;

import com.finance.portal.admin.infrastructure.persistence.entity.UserBanStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface UserBanStateRepository extends JpaRepository<UserBanStateEntity, String> {

    List<UserBanStateEntity> findByKeycloakUserIdIn(Collection<String> keycloakUserIds);

    @Query("""
            SELECT u FROM UserBanStateEntity u
            WHERE u.permanent = false
              AND u.banUntil IS NOT NULL
              AND u.banUntil <= :now
            """)
    List<UserBanStateEntity> findExpiredTemporaryBans(@Param("now") Instant now);
}
