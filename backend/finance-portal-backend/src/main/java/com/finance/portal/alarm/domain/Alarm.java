package com.finance.portal.alarm.domain;

import com.finance.portal.common.domain.AssetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kullanıcının bir enstrüman için kurduğu alarm. Periyodik olarak
 * (bkz. {@code AlarmEvaluationService}) değerlendirilir; koşul sağlanınca
 * bildirim üretir ve kullanıcının onaylı e-postasına mail gönderir.
 */
@Entity
@Table(name = "alarm")
@Getter
@Setter
@NoArgsConstructor
public class Alarm {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /** Alarm kurulurken JWT'den yakalanan onaylı e-posta (tetiklenince buraya gönderilir). */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @Column(name = "symbol", nullable = false, length = 120)
    private String symbol;

    @Column(name = "instrument_name", length = 255)
    private String instrumentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric", nullable = false, length = 20)
    private AlarmMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private AlarmDirection direction;

    @Column(name = "threshold", nullable = false, precision = 24, scale = 8)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private AlarmFrequency frequency = AlarmFrequency.ONCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlarmStatus status = AlarmStatus.ACTIVE;

    /** Kullanıcı banlandığında pasifleştirilen alarmları işaretler; unban'da yalnızca bunlar geri ACTIVE yapılır. */
    @Column(name = "disabled_by_ban", nullable = false)
    private boolean disabledByBan = false;

    /** Son değerlendirmede gözlenen metrik değeri (UI'da "şu an" göstermek için). */
    @Column(name = "last_observed_value", precision = 24, scale = 8)
    private BigDecimal lastObservedValue;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
