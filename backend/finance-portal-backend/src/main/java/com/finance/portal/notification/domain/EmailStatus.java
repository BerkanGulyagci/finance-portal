package com.finance.portal.notification.domain;

/**
 * Bildirime bağlı e-postanın gönderim durumu.
 * <ul>
 *   <li>{@code PENDING} — henüz gönderilmedi</li>
 *   <li>{@code SENT} — başarıyla gönderildi</li>
 *   <li>{@code FAILED} — gönderim hatası ({@code emailError} doldurulur)</li>
 *   <li>{@code SKIPPED} — alıcı e-posta yok / doğrulanmamış, gönderilmedi</li>
 * </ul>
 */
public enum EmailStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED
}
