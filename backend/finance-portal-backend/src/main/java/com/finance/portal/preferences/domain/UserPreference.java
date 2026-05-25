package com.finance.portal.preferences.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kullanıcının arayüz tercihi (anahtar-değer). value = JSON metni; cihazlar arası senkron edilir.
 * Her (user_id, pref_key) çifti benzersiz.
 */
@Entity
@Table(name = "user_preference",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_preference", columnNames = {"user_id", "pref_key"}))
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "pref_key", nullable = false, length = 255)
    private String prefKey;

    @Column(name = "value", columnDefinition = "text")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
