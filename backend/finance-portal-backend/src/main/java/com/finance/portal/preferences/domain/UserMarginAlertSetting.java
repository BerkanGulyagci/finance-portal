package com.finance.portal.preferences.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Kullanıcı başına tek VİOP teminat-uyarı eşiği. {@code marginAlertThresholdPct} 0–100 aralığında,
 * 0 = uyarı kapalı, varsayılan 50. Açık FUTURE pozisyonlarının teminat oranı bu eşik altına
 * düşerse {@code UserMarginScannerService} margin call bildirimi tetikler.
 */
@Entity
@Table(name = "user_margin_alert_setting")
@Getter
@Setter
@NoArgsConstructor
public class UserMarginAlertSetting {

    @Id
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "margin_alert_threshold_pct", nullable = false)
    private Integer marginAlertThresholdPct = 50;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserMarginAlertSetting(String userId, Integer marginAlertThresholdPct) {
        this.userId = userId;
        this.marginAlertThresholdPct = marginAlertThresholdPct;
    }

    @PrePersist
    public void prePersist() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (marginAlertThresholdPct == null) {
            marginAlertThresholdPct = 50;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
