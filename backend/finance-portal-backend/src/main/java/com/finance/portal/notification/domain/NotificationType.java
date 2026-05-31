package com.finance.portal.notification.domain;

/**
 * Bildirim türü. Alarm tetiklenmeleri, hesap banı, bülten gönderimleri, destek talebi güncellemeleri,
 * sistem/admin hatırlatmaları, portföy olayları (ör. DİBS itfa kapanışı) ve VİOP teminat uyarıları.
 */
public enum NotificationType {
    ALARM,
    BAN,
    NEWSLETTER,
    SUPPORT,
    ADMIN,
    /** Portföy otomasyonu olayları — vade sonu itfa, kupon hatırlatma vb. */
    PORTFOLIO,
    /** VİOP teminat oranı kritik eşiğin altına düştüğünde (gerçek brokerda margin call). */
    MARGIN_CALL
}
