package com.finance.portal.alarm.domain;

/**
 * Alarm durumu.
 * <ul>
 *   <li>{@code ACTIVE} — değerlendirilmeye devam eder</li>
 *   <li>{@code TRIGGERED} — tek-seferlik alarm tetiklendi, artık değerlendirilmez</li>
 *   <li>{@code DISABLED} — kullanıcı duraklattı, değerlendirilmez</li>
 * </ul>
 */
public enum AlarmStatus {
    ACTIVE,
    TRIGGERED,
    DISABLED
}
