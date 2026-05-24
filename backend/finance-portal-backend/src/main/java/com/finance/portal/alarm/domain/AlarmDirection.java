package com.finance.portal.alarm.domain;

/**
 * Alarm koşulunun yönü.
 * <ul>
 *   <li>{@code ABOVE} — gözlenen değer eşiğin üzerine çıkarsa tetiklenir ("Üzerine Çıkarsa")</li>
 *   <li>{@code BELOW} — gözlenen değer eşiğin altına inerse tetiklenir ("Altına İnerse")</li>
 * </ul>
 */
public enum AlarmDirection {
    ABOVE,
    BELOW
}
