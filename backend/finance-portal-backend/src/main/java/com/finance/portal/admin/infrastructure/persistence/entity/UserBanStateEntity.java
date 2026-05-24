package com.finance.portal.admin.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_ban_state")
@Getter
@Setter
@NoArgsConstructor
public class UserBanStateEntity {

    @Id
    @Column(name = "keycloak_user_id", nullable = false, length = 36)
    private String keycloakUserId;

    @Column(nullable = false)
    private boolean permanent;

    @Column(name = "ban_until")
    private Instant banUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ban_reason", length = 512)
    private String banReason;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
