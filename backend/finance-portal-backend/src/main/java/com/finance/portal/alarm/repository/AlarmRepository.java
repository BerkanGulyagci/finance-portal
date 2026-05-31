package com.finance.portal.alarm.repository;

import com.finance.portal.alarm.domain.Alarm;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.alarm.domain.AlarmStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlarmRepository extends JpaRepository<Alarm, UUID> {

    List<Alarm> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<Alarm> findByIdAndUserId(UUID id, String userId);

    /** Değerlendirme döngüsü için aktif alarmlar. */
    List<Alarm> findByStatus(AlarmStatus status);

    /** MarginAlarmEvaluator için yalnız MARGIN_RATIO alarmları (PRICE alarmlarını dolaşmadan). */
    List<Alarm> findByStatusAndMetric(AlarmStatus status, AlarmMetric metric);
}
