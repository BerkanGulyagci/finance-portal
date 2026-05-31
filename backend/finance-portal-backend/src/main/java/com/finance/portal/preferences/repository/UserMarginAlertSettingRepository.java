package com.finance.portal.preferences.repository;

import com.finance.portal.preferences.domain.UserMarginAlertSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMarginAlertSettingRepository extends JpaRepository<UserMarginAlertSetting, String> {

    Optional<UserMarginAlertSetting> findByUserId(String userId);
}
