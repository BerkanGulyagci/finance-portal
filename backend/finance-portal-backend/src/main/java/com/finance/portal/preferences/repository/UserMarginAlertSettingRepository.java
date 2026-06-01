package com.finance.portal.preferences.repository;

import com.finance.portal.preferences.domain.UserMarginAlertSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * VİOP teminat-uyarı eşiği per-user kaydı. Açıkça {@code @Repository} ile işaretlendi ki
 * Spring Data çoklu-store (JPA + Redis) tespitinde "Could not safely identify store assignment"
 * uyarısı üretmesin; saklama JPA tablosu {@code user_margin_alert_setting}.
 */
@Repository
public interface UserMarginAlertSettingRepository extends JpaRepository<UserMarginAlertSetting, String> {

    Optional<UserMarginAlertSetting> findByUserId(String userId);
}
