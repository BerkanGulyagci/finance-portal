package com.finance.portal.alarm.application.service;

import com.finance.portal.alarm.application.model.AlarmMarketSnapshot;
import com.finance.portal.alarm.application.model.AlarmTriggeredEvent;
import com.finance.portal.alarm.application.port.AlarmMarketDataPort;
import com.finance.portal.alarm.application.port.AlarmNotificationPort;
import com.finance.portal.alarm.domain.Alarm;
import com.finance.portal.alarm.domain.AlarmDirection;
import com.finance.portal.alarm.domain.AlarmFrequency;
import com.finance.portal.alarm.domain.AlarmStatus;
import com.finance.portal.alarm.repository.AlarmRepository;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aktif alarmları periyodik olarak değerlendirir; koşul sağlanınca bildirim/e-posta üretir.
 * Tek-seferlik (ONCE) alarmlar tetiklenince TRIGGERED olur; tekrarlı (RECURRING) alarmlar
 * cooldown süresi dolmadan tekrar tetiklenmez.
 */
@Service
public class AlarmEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlarmEvaluationService.class);

    private final AlarmRepository alarmRepository;
    private final AlarmMarketDataPort marketDataPort;
    private final AlarmNotificationPort notificationPort;
    private final CentralBusinessLogService businessLog;
    private final boolean enabled;
    private final long recurringCooldownMinutes;

    public AlarmEvaluationService(AlarmRepository alarmRepository,
                                  AlarmMarketDataPort marketDataPort,
                                  AlarmNotificationPort notificationPort,
                                  CentralBusinessLogService businessLog,
                                  @Value("${alarm.evaluation.enabled:true}") boolean enabled,
                                  @Value("${alarm.recurring-cooldown-minutes:60}") long recurringCooldownMinutes) {
        this.alarmRepository = alarmRepository;
        this.marketDataPort = marketDataPort;
        this.notificationPort = notificationPort;
        this.businessLog = businessLog;
        this.enabled = enabled;
        this.recurringCooldownMinutes = recurringCooldownMinutes;
    }

    @Scheduled(
            fixedRateString = "${alarm.evaluation.fixed-rate-ms:60000}",
            initialDelayString = "${alarm.evaluation.initial-delay-ms:20000}")
    public void evaluateActiveAlarms() {
        if (!enabled) {
            return;
        }
        List<Alarm> active = alarmRepository.findByStatus(AlarmStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        log.debug("Evaluating {} active alarm(s)", active.size());
        for (Alarm alarm : active) {
            try {
                evaluateOne(alarm);
            } catch (Exception ex) {
                log.warn("Alarm {} evaluation failed: {}", alarm.getId(), ex.getMessage());
            }
        }
    }

    private void evaluateOne(Alarm alarm) {
        AlarmMarketSnapshot snapshot = marketDataPort.probe(alarm.getAssetType(), alarm.getSymbol());
        if (snapshot == null) {
            return;
        }
        BigDecimal observed = snapshot.valueFor(alarm.getMetric());
        if (observed == null) {
            return; // bu metrik bu tip için yok → atla
        }

        alarm.setLastObservedValue(observed);

        boolean conditionMet = isConditionMet(alarm.getDirection(), observed, alarm.getThreshold());
        if (!conditionMet) {
            alarmRepository.save(alarm);
            return;
        }

        // RECURRING: cooldown dolmadıysa tetikleme (ama gözlenen değeri kaydet)
        if (alarm.getFrequency() == AlarmFrequency.RECURRING && alarm.getLastTriggeredAt() != null) {
            LocalDateTime nextAllowed = alarm.getLastTriggeredAt().plusMinutes(recurringCooldownMinutes);
            if (LocalDateTime.now().isBefore(nextAllowed)) {
                alarmRepository.save(alarm);
                return;
            }
        }

        trigger(alarm, observed);
    }

    private void trigger(Alarm alarm, BigDecimal observed) {
        LocalDateTime now = LocalDateTime.now();
        alarm.setLastTriggeredAt(now);
        if (alarm.getTriggeredAt() == null) {
            alarm.setTriggeredAt(now);
        }
        if (alarm.getFrequency() == AlarmFrequency.ONCE) {
            alarm.setStatus(AlarmStatus.TRIGGERED);
        }
        alarmRepository.save(alarm);

        AlarmTriggeredEvent event = new AlarmTriggeredEvent(
                alarm.getUserId(), alarm.getRecipientEmail(), alarm.getId(),
                alarm.getAssetType(), alarm.getSymbol(), alarm.getInstrumentName(),
                alarm.getMetric(), alarm.getDirection(), alarm.getThreshold(), observed, alarm.getNote());

        notificationPort.notifyAlarmTriggered(event);
        log.info("Alarm {} triggered for user {} ({} {} {})",
                alarm.getId(), alarm.getUserId(), alarm.getSymbol(), alarm.getDirection(), alarm.getThreshold());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("symbol", alarm.getSymbol());
        meta.put("assetType", alarm.getAssetType() != null ? alarm.getAssetType().name() : null);
        meta.put("metric", alarm.getMetric() != null ? alarm.getMetric().name() : null);
        meta.put("direction", alarm.getDirection() != null ? alarm.getDirection().name() : null);
        meta.put("threshold", alarm.getThreshold());
        meta.put("observed", observed);
        businessLog.publish(BusinessLogSupport.CATEGORY_AUDIT, BusinessLogSupport.EVENT_ALARM_TRIGGERED, "INFO",
                "Alarm triggered: " + alarm.getSymbol(),
                BusinessLogSupport.ENTITY_ALARM, alarm.getId() != null ? alarm.getId().toString() : null,
                BusinessLogSupport.ACTION_TRIGGER, BusinessLogSupport.RESULT_SUCCESS, meta,
                alarm.getUserId(), AlarmEvaluationService.class.getName());
    }

    private static boolean isConditionMet(AlarmDirection direction, BigDecimal observed, BigDecimal threshold) {
        if (observed == null || threshold == null) {
            return false;
        }
        int cmp = observed.compareTo(threshold);
        // "Üzerine çıkarsa" = kesinlikle büyük (>), "Altına inerse" = kesinlikle küçük (<).
        // Eşitlikte tetiklenmez → eşik o anki fiyata eşit kurulduğunda anında tetiklenmez.
        return direction == AlarmDirection.ABOVE ? cmp > 0 : cmp < 0;
    }
}
