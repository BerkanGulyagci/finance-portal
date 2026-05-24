package com.finance.portal.alarm.application.port;

import com.finance.portal.alarm.application.model.AlarmTriggeredEvent;

/**
 * Alarm tetiklendiğinde bildirim (uygulama-içi + e-posta) üretmek için port.
 * Implementasyonu notification modülündedir; alarm modülü yalnızca bu arayüze bağımlıdır.
 */
public interface AlarmNotificationPort {

    void notifyAlarmTriggered(AlarmTriggeredEvent event);
}
