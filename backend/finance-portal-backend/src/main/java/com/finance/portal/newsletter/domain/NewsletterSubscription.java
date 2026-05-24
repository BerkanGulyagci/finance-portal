package com.finance.portal.newsletter.domain;

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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kullanıcının dashboard özeti e-postası (bülten) aboneliği. Seçilen sıklıkta
 * (bkz. {@code NewsletterDigestService}) portföy + piyasa özeti e-posta ile gönderilir.
 * Her kullanıcı için en fazla bir abonelik kaydı tutulur (user_id benzersiz).
 */
@Entity
@Table(name = "newsletter_subscription")
@Getter
@Setter
@NoArgsConstructor
public class NewsletterSubscription {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100, unique = true)
    private String userId;

    /** Abonelik kurulurken JWT'den yakalanan onaylı e-posta (digest buraya gönderilir). */
    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private NewsletterFrequency frequency = NewsletterFrequency.WEEKLY;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Kullanıcı banlandığında pasifleştirilen aboneliği işaretler; unban'da yalnızca bu durumda geri açılır. */
    @Column(name = "disabled_by_ban", nullable = false)
    private boolean disabledByBan = false;

    /** E-postadaki "abonelikten çık" linki için kimlik doğrulamasız token. */
    @Column(name = "unsubscribe_token", nullable = false, length = 64, updatable = false)
    private String unsubscribeToken;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (unsubscribeToken == null) {
            unsubscribeToken = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
