package com.finance.portal.alarm.application.service;

import com.finance.portal.alarm.application.model.AlarmMarketSnapshot;
import com.finance.portal.alarm.application.model.AlarmTriggeredEvent;
import com.finance.portal.alarm.application.port.AlarmMarketDataPort;
import com.finance.portal.alarm.application.port.AlarmNotificationPort;
import com.finance.portal.alarm.domain.Alarm;
import com.finance.portal.alarm.domain.AlarmDirection;
import com.finance.portal.alarm.domain.AlarmFrequency;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.alarm.domain.AlarmStatus;
import com.finance.portal.alarm.repository.AlarmRepository;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlarmEvaluationServiceTest {

    private AlarmRepository repo;
    private AlarmMarketDataPort marketData;
    private AlarmNotificationPort notification;
    private CentralBusinessLogService businessLog;
    private AlarmEvaluationService service;

    @BeforeEach
    void setUp() {
        repo = mock(AlarmRepository.class);
        marketData = mock(AlarmMarketDataPort.class);
        notification = mock(AlarmNotificationPort.class);
        businessLog = mock(CentralBusinessLogService.class);
        service = new AlarmEvaluationService(repo, marketData, notification, businessLog,
                true /* enabled */, 60 /* cooldown minutes */);
    }

    // ------------------------------ feature flag / boş liste ------------------------------

    @Test
    @DisplayName("evaluateActiveAlarms: enabled=false → erken çıkar, repo'ya bakmaz")
    void evaluate_disabled_noop() {
        AlarmEvaluationService disabled = new AlarmEvaluationService(
                repo, marketData, notification, businessLog, false, 60);

        disabled.evaluateActiveAlarms();

        verifyNoInteractions(repo, marketData, notification);
    }

    @Test
    @DisplayName("evaluateActiveAlarms: aktif alarm yok → snapshot çağrılmaz")
    void evaluate_noActiveAlarms_skipsMarketData() {
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of());

        service.evaluateActiveAlarms();

        verify(repo).findByStatus(AlarmStatus.ACTIVE);
        verifyNoInteractions(marketData, notification);
    }

    // ------------------------------ snapshot null / metric yok ------------------------------

    @Test
    @DisplayName("evaluateActiveAlarms: snapshot null → alarm dokunulmaz, kaydedilmez")
    void evaluate_nullSnapshot_skipsAlarm() {
        Alarm a = alarm("THYAO", AlarmMetric.PRICE, AlarmDirection.ABOVE, "300", AlarmFrequency.ONCE);
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of(a));
        when(marketData.probe(AssetType.STOCK, "THYAO")).thenReturn(null);

        service.evaluateActiveAlarms();

        verify(repo, never()).save(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("evaluateActiveAlarms: metric değeri yok → atla, kaydetme")
    void evaluate_metricValueNull_skipsAlarm() {
        Alarm a = alarm("THYAO", AlarmMetric.VOLUME, AlarmDirection.ABOVE, "1000", AlarmFrequency.ONCE);
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of(a));
        // Snapshot var ama VOLUME alanı null
        AlarmMarketSnapshot snap = mock(AlarmMarketSnapshot.class);
        when(snap.valueFor(AlarmMetric.VOLUME)).thenReturn(null);
        when(marketData.probe(any(), any())).thenReturn(snap);

        service.evaluateActiveAlarms();

        verify(repo, never()).save(any());
    }

    // ------------------------------ koşul ÜZERİNE çıkmaz / iner ------------------------------

    @Test
    @DisplayName("evaluateActiveAlarms: ABOVE & observed = threshold → tetiklenmez (eşitlikte sayılmaz)")
    void evaluate_aboveEqualThreshold_doesNotTrigger() {
        Alarm a = alarm("THYAO", AlarmMetric.PRICE, AlarmDirection.ABOVE, "300", AlarmFrequency.ONCE);
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of(a));
        AlarmMarketSnapshot snap = mock(AlarmMarketSnapshot.class);
        when(snap.valueFor(AlarmMetric.PRICE)).thenReturn(new BigDecimal("300"));  // eşit
        when(marketData.probe(any(), any())).thenReturn(snap);

        service.evaluateActiveAlarms();

        verify(repo).save(a);                     // gözlenen değer saved
        assertThat(a.getStatus()).isEqualTo(AlarmStatus.ACTIVE);  // status değişmedi
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("evaluateActiveAlarms: ABOVE & observed > threshold → tetiklenir, ONCE → TRIGGERED")
    void evaluate_aboveCrossed_oncesTrigger() {
        Alarm a = alarm("THYAO", AlarmMetric.PRICE, AlarmDirection.ABOVE, "300", AlarmFrequency.ONCE);
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of(a));
        AlarmMarketSnapshot snap = mock(AlarmMarketSnapshot.class);
        when(snap.valueFor(AlarmMetric.PRICE)).thenReturn(new BigDecimal("305"));
        when(marketData.probe(any(), any())).thenReturn(snap);

        service.evaluateActiveAlarms();

        assertThat(a.getStatus()).isEqualTo(AlarmStatus.TRIGGERED);
        assertThat(a.getTriggeredAt()).isNotNull();
        assertThat(a.getLastTriggeredAt()).isNotNull();
        assertThat(a.getLastObservedValue()).isEqualByComparingTo("305");
        verify(notification).notifyAlarmTriggered(any(AlarmTriggeredEvent.class));
        verify(businessLog).publish(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("evaluateActiveAlarms: BELOW & observed < threshold → tetiklenir")
    void evaluate_belowCrossed_triggers() {
        Alarm a = alarm("BTC", AlarmMetric.PRICE, AlarmDirection.BELOW, "50000", AlarmFrequency.ONCE);
        a.setAssetType(AssetType.CRYPTO);
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of(a));
        AlarmMarketSnapshot snap = mock(AlarmMarketSnapshot.class);
        when(snap.valueFor(AlarmMetric.PRICE)).thenReturn(new BigDecimal("48000"));
        when(marketData.probe(any(), any())).thenReturn(snap);

        service.evaluateActiveAlarms();

        assertThat(a.getStatus()).isEqualTo(AlarmStatus.TRIGGERED);
        verify(notification).notifyAlarmTriggered(any());
    }

    // ------------------------------ RECURRING + cooldown ------------------------------

    @Test
    @DisplayName("evaluateActiveAlarms: RECURRING, cooldown dolmadan tetiklenmez")
    void evaluate_recurring_withinCooldown_doesNotTrigger() {
        Alarm a = alarm("THYAO", AlarmMetric.PRICE, AlarmDirection.ABOVE, "300", AlarmFrequency.RECURRING);
        a.setLastTriggeredAt(LocalDateTime.now().minusMinutes(10));  // cooldown 60 dk → henüz dolmadı
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of(a));
        AlarmMarketSnapshot snap = mock(AlarmMarketSnapshot.class);
        when(snap.valueFor(AlarmMetric.PRICE)).thenReturn(new BigDecimal("305"));
        when(marketData.probe(any(), any())).thenReturn(snap);

        service.evaluateActiveAlarms();

        verify(repo).save(a);                                  // observed value saved
        assertThat(a.getStatus()).isEqualTo(AlarmStatus.ACTIVE);  // RECURRING TRIGGERED olmaz
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("evaluateActiveAlarms: RECURRING, cooldown dolduktan sonra tekrar tetiklenir; status ACTIVE kalır")
    void evaluate_recurring_afterCooldown_triggersAgain() {
        Alarm a = alarm("THYAO", AlarmMetric.PRICE, AlarmDirection.ABOVE, "300", AlarmFrequency.RECURRING);
        a.setLastTriggeredAt(LocalDateTime.now().minusMinutes(120));  // 60 dk cooldown dolmuş
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of(a));
        AlarmMarketSnapshot snap = mock(AlarmMarketSnapshot.class);
        when(snap.valueFor(AlarmMetric.PRICE)).thenReturn(new BigDecimal("305"));
        when(marketData.probe(any(), any())).thenReturn(snap);

        service.evaluateActiveAlarms();

        // RECURRING → status TRIGGERED olmaz, ACTIVE kalır
        assertThat(a.getStatus()).isEqualTo(AlarmStatus.ACTIVE);
        verify(notification).notifyAlarmTriggered(any());
    }

    // ------------------------------ hata tolere ------------------------------

    @Test
    @DisplayName("evaluateActiveAlarms: bir alarm patlarsa diğerleri etkilenmez")
    void evaluate_oneFails_othersContinue() {
        Alarm a1 = alarm("THYAO", AlarmMetric.PRICE, AlarmDirection.ABOVE, "300", AlarmFrequency.ONCE);
        Alarm a2 = alarm("BTC", AlarmMetric.PRICE, AlarmDirection.ABOVE, "50000", AlarmFrequency.ONCE);
        when(repo.findByStatus(AlarmStatus.ACTIVE)).thenReturn(List.of(a1, a2));
        // a1: probe throws
        when(marketData.probe(AssetType.STOCK, "THYAO")).thenThrow(new RuntimeException("upstream down"));
        // a2: normal trigger
        when(marketData.probe(AssetType.STOCK, "BTC")).thenAnswer(inv -> {
            AlarmMarketSnapshot s = mock(AlarmMarketSnapshot.class);
            when(s.valueFor(AlarmMetric.PRICE)).thenReturn(new BigDecimal("60000"));
            return s;
        });

        service.evaluateActiveAlarms();

        // a2 tetiklenmiş olmalı
        verify(notification).notifyAlarmTriggered(any());
    }

    // ------------------------------ helper ------------------------------

    private static Alarm alarm(String symbol, AlarmMetric metric, AlarmDirection dir,
                               String threshold, AlarmFrequency freq) {
        Alarm a = new Alarm();
        a.setId(UUID.randomUUID());
        a.setUserId("u1");
        a.setRecipientEmail("u1@example.com");
        a.setSymbol(symbol);
        a.setAssetType(AssetType.STOCK);
        a.setMetric(metric);
        a.setDirection(dir);
        a.setThreshold(new BigDecimal(threshold));
        a.setFrequency(freq);
        a.setStatus(AlarmStatus.ACTIVE);
        return a;
    }
}
