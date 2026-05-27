package com.finance.portal.notification.domain;

/**
 * Bildirim türü. Alarm tetiklenmeleri, hesap banı, bülten gönderimleri, destek talebi güncellemeleri
 * ve sistem/admin hatırlatmaları (ör. HMB aylık Eurobond listesi yayımlandı uyarısı).
 */
public enum NotificationType {
    ALARM,
    BAN,
    NEWSLETTER,
    SUPPORT,
    ADMIN
}
