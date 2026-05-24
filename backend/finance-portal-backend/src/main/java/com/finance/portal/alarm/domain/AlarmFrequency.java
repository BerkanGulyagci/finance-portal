package com.finance.portal.alarm.domain;

/**
 * Alarmın tekrar davranışı.
 * <ul>
 *   <li>{@code ONCE} — bir kez tetiklenir, sonra {@link AlarmStatus#TRIGGERED} olur ("Tek Seferlik")</li>
 *   <li>{@code RECURRING} — koşul her sağlandığında tetiklenir (cooldown ile), aktif kalır</li>
 * </ul>
 */
public enum AlarmFrequency {
    ONCE,
    RECURRING
}
