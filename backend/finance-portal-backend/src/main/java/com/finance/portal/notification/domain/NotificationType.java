package com.finance.portal.notification.domain;

/**
 * Bildirim türü. Alarm tetiklenmeleri, hesap banı, bülten gönderimleri, destek talebi güncellemeleri,
 * sistem/admin hatırlatmaları ve portföy olayları (ör. DİBS itfa kapanışı).
 */
public enum NotificationType {
    ALARM,
    BAN,
    NEWSLETTER,
    SUPPORT,
    ADMIN,
    /** Portföy otomasyonu olayları — vade sonu itfa, kupon hatırlatma vb. */
    PORTFOLIO
}
