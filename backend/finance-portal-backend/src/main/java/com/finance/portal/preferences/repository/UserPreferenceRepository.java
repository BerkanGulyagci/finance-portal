package com.finance.portal.preferences.repository;

import com.finance.portal.preferences.domain.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {

    List<UserPreference> findByUserId(String userId);

    Optional<UserPreference> findByUserIdAndPrefKey(String userId, String prefKey);
}
