package com.finance.portal.support.domain;

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
 * Kullanıcının açtığı destek talebi. Kullanıcı yalnız OPEN durumdayken düzenleyebilir/silebilir;
 * admin durumu (OPEN→IN_PROGRESS→RESOLVED) ve not'u günceller, her değişimde kullanıcıya bildirim gider.
 */
@Entity
@Table(name = "support_ticket")
@Getter
@Setter
@NoArgsConstructor
public class SupportTicket {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /** Talep oluşturulurken JWT'den yakalanan e-posta (bildirim buraya gönderilir). */
    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportTicketStatus status = SupportTicketStatus.OPEN;

    /** Admin'in talebe eklediği not (kullanıcıya bildirimde de gösterilir). */
    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = SupportTicketStatus.OPEN;
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
