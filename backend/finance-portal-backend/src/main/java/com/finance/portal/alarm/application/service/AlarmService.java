package com.finance.portal.alarm.application.service;

import com.finance.portal.alarm.domain.Alarm;
import com.finance.portal.alarm.domain.AlarmDirection;
import com.finance.portal.alarm.domain.AlarmFrequency;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.alarm.domain.AlarmStatus;
import com.finance.portal.alarm.presentation.dto.CreateAlarmRequest;
import com.finance.portal.alarm.presentation.dto.UpdateAlarmRequest;
import com.finance.portal.alarm.repository.AlarmRepository;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Alarm CRUD iş kuralları. Tüm sorgular kullanıcıya (userId) göre kapsanır.
 */
@Service
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final CentralBusinessLogService businessLog;

    public AlarmService(AlarmRepository alarmRepository, CentralBusinessLogService businessLog) {
        this.alarmRepository = alarmRepository;
        this.businessLog = businessLog;
    }

    private void audit(String eventType, String action, Alarm a) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("symbol", a.getSymbol());
        meta.put("assetType", a.getAssetType() != null ? a.getAssetType().name() : null);
        meta.put("metric", a.getMetric() != null ? a.getMetric().name() : null);
        meta.put("direction", a.getDirection() != null ? a.getDirection().name() : null);
        meta.put("threshold", a.getThreshold());
        businessLog.publish(BusinessLogSupport.CATEGORY_AUDIT, eventType, "INFO",
                "Alarm " + action.toLowerCase() + ": " + a.getSymbol(),
                BusinessLogSupport.ENTITY_ALARM, a.getId() != null ? a.getId().toString() : null,
                action, BusinessLogSupport.RESULT_SUCCESS, meta, a.getUserId(), AlarmService.class.getName());
    }

    @WithSpan("AlarmService.create")
    @Transactional
    public Alarm create(@SpanAttribute("alarm.user_id") String userId, String recipientEmail, CreateAlarmRequest req) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Span.current().setAttribute("alarm.symbol", String.valueOf(req.getSymbol()));
        Span.current().setAttribute("alarm.asset_type", String.valueOf(req.getAssetType()));
        Alarm a = new Alarm();
        a.setUserId(userId);
        a.setRecipientEmail(recipientEmail);
        a.setAssetType(parseAssetType(req.getAssetType()));
        a.setSymbol(requireText(req.getSymbol(), "symbol").trim());
        a.setInstrumentName(req.getInstrumentName());
        a.setMetric(parseMetric(req.getMetric()));
        a.setDirection(parseDirection(req.getDirection()));
        a.setThreshold(requireThreshold(req.getThreshold()));
        a.setFrequency(parseFrequency(req.getFrequency()));
        a.setNote(req.getNote());
        a.setStatus(AlarmStatus.ACTIVE);
        Alarm saved = alarmRepository.save(a);
        Span.current().setAttribute("alarm.id", String.valueOf(saved.getId()));
        audit(BusinessLogSupport.EVENT_ALARM_CREATED, BusinessLogSupport.ACTION_CREATE, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Alarm> list(String userId) {
        return alarmRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Alarm get(String userId, UUID id) {
        return alarmRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Alarm not found: " + id));
    }

    @WithSpan("AlarmService.update")
    @Transactional
    public Alarm update(@SpanAttribute("alarm.user_id") String userId, UUID id, UpdateAlarmRequest req) {
        Span.current().setAttribute("alarm.id", String.valueOf(id));
        Alarm a = get(userId, id);
        if (req.getMetric() != null) {
            a.setMetric(parseMetric(req.getMetric()));
        }
        if (req.getDirection() != null) {
            a.setDirection(parseDirection(req.getDirection()));
        }
        if (req.getThreshold() != null) {
            a.setThreshold(requireThreshold(req.getThreshold()));
        }
        if (req.getFrequency() != null) {
            a.setFrequency(parseFrequency(req.getFrequency()));
        }
        if (req.getNote() != null) {
            a.setNote(req.getNote());
        }
        if (req.getStatus() != null) {
            AlarmStatus newStatus = parseStatus(req.getStatus());
            // Yeniden etkinleştirilirken tetik zamanlarını sıfırla
            if (newStatus == AlarmStatus.ACTIVE) {
                a.setTriggeredAt(null);
            }
            a.setStatus(newStatus);
        }
        Alarm saved = alarmRepository.save(a);
        audit(BusinessLogSupport.EVENT_ALARM_UPDATED, BusinessLogSupport.ACTION_UPDATE, saved);
        return saved;
    }

    @WithSpan("AlarmService.delete")
    @Transactional
    public void delete(@SpanAttribute("alarm.user_id") String userId, UUID id) {
        Span.current().setAttribute("alarm.id", String.valueOf(id));
        Alarm a = get(userId, id);
        alarmRepository.delete(a);
        audit(BusinessLogSupport.EVENT_ALARM_DELETED, BusinessLogSupport.ACTION_DELETE, a);
    }

    /**
     * Bir kullanıcının tüm AKTİF alarmlarını pasifleştirir (örn. kullanıcı banlandığında).
     * @return pasifleştirilen alarm sayısı
     */
    @WithSpan("AlarmService.disableAllForUser")
    @Transactional
    public int disableAllForUser(@SpanAttribute("alarm.user_id") String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        List<Alarm> alarms = alarmRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int disabled = 0;
        for (Alarm a : alarms) {
            if (a.getStatus() == AlarmStatus.ACTIVE) {
                a.setStatus(AlarmStatus.DISABLED);
                a.setDisabledByBan(true);
                alarmRepository.save(a);
                audit(BusinessLogSupport.EVENT_ALARM_UPDATED, BusinessLogSupport.ACTION_UPDATE, a);
                disabled++;
            }
        }
        Span.current().setAttribute("alarm.disabled_count", disabled);
        return disabled;
    }

    /**
     * Ban kalkınca, YALNIZCA ban yüzünden pasifleştirilmiş alarmları yeniden ACTIVE yapar
     * (kullanıcının ban'dan önce kendi kapattıkları korunur).
     * @return yeniden etkinleştirilen alarm sayısı
     */
    @WithSpan("AlarmService.reenableBanDisabledForUser")
    @Transactional
    public int reenableBanDisabledForUser(@SpanAttribute("alarm.user_id") String userId) {
        if (userId == null || userId.isBlank()) {
            return 0;
        }
        List<Alarm> alarms = alarmRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int reenabled = 0;
        for (Alarm a : alarms) {
            if (a.isDisabledByBan()) {
                a.setStatus(AlarmStatus.ACTIVE);
                a.setDisabledByBan(false);
                a.setTriggeredAt(null);
                alarmRepository.save(a);
                audit(BusinessLogSupport.EVENT_ALARM_UPDATED, BusinessLogSupport.ACTION_UPDATE, a);
                reenabled++;
            }
        }
        Span.current().setAttribute("alarm.reenabled_count", reenabled);
        return reenabled;
    }

    // ── parse helpers ──────────────────────────────────────────────────────────

    // NOT: toUpperCase() platform locale'ine duyarlı; Türkçe locale'de "i" → "İ" olur ve
    // "price" gibi küçük-harf girdiler enum sabitleriyle (PRICE) eşleşmez. Locale.ROOT
    // determinist büyük-harf yapımı sağlar (S1449 / S1640 ailesinden Sonar uyarısı).
    private static AssetType parseAssetType(String v) {
        try {
            return AssetType.valueOf(requireText(v, "assetType").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid assetType: " + v);
        }
    }

    private static AlarmMetric parseMetric(String v) {
        try {
            return AlarmMetric.valueOf(requireText(v, "metric").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid metric: " + v);
        }
    }

    private static AlarmDirection parseDirection(String v) {
        try {
            return AlarmDirection.valueOf(requireText(v, "direction").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid direction: " + v);
        }
    }

    private static AlarmFrequency parseFrequency(String v) {
        if (v == null || v.isBlank()) {
            return AlarmFrequency.ONCE;
        }
        try {
            return AlarmFrequency.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid frequency: " + v);
        }
    }

    private static AlarmStatus parseStatus(String v) {
        try {
            return AlarmStatus.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status: " + v);
        }
    }

    private static String requireText(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return v;
    }

    private static BigDecimal requireThreshold(BigDecimal v) {
        if (v == null) {
            throw new IllegalArgumentException("threshold must not be null");
        }
        return v;
    }
}
