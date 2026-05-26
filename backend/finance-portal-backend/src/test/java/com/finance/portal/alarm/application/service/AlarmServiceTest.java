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
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.domain.AssetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlarmServiceTest {

    private AlarmRepository repo;
    private CentralBusinessLogService businessLog;
    private AlarmService service;

    private static final String USER = "u1";
    private static final String EMAIL = "u1@example.com";

    @BeforeEach
    void setUp() {
        repo = mock(AlarmRepository.class);
        businessLog = mock(CentralBusinessLogService.class);
        // save returns the same alarm with an assigned id
        when(repo.save(any(Alarm.class))).thenAnswer(inv -> {
            Alarm a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });
        service = new AlarmService(repo, businessLog);
    }

    // ============================================================================
    // create()
    // ============================================================================

    @Test
    @DisplayName("create: happy path → repo.save + audit log; alanlar doğru set edilir")
    void create_happyPath() {
        CreateAlarmRequest req = req("STOCK", "THYAO", "PRICE", "ABOVE", "300", "ONCE");

        Alarm saved = service.create(USER, EMAIL, req);

        ArgumentCaptor<Alarm> cap = ArgumentCaptor.forClass(Alarm.class);
        verify(repo).save(cap.capture());
        Alarm a = cap.getValue();
        assertThat(a.getUserId()).isEqualTo(USER);
        assertThat(a.getRecipientEmail()).isEqualTo(EMAIL);
        assertThat(a.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(a.getSymbol()).isEqualTo("THYAO");
        assertThat(a.getMetric()).isEqualTo(AlarmMetric.PRICE);
        assertThat(a.getDirection()).isEqualTo(AlarmDirection.ABOVE);
        assertThat(a.getThreshold()).isEqualByComparingTo("300");
        assertThat(a.getFrequency()).isEqualTo(AlarmFrequency.ONCE);
        assertThat(a.getStatus()).isEqualTo(AlarmStatus.ACTIVE);
        assertThat(saved.getId()).isNotNull();
        // audit log çağrılır
        verify(businessLog).publish(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("create: frequency yoksa varsayılan ONCE")
    void create_defaultFrequencyIsOnce() {
        CreateAlarmRequest req = req("CRYPTO", "BTC", "PRICE", "ABOVE", "50000", null);

        service.create(USER, EMAIL, req);

        ArgumentCaptor<Alarm> cap = ArgumentCaptor.forClass(Alarm.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getFrequency()).isEqualTo(AlarmFrequency.ONCE);
    }

    @Test
    @DisplayName("create: case-insensitive enum parsing (stock / above / price)")
    void create_caseInsensitiveEnums() {
        CreateAlarmRequest req = req("stock", "thyao", "price", "above", "300", "recurring");

        service.create(USER, EMAIL, req);

        ArgumentCaptor<Alarm> cap = ArgumentCaptor.forClass(Alarm.class);
        verify(repo).save(cap.capture());
        Alarm a = cap.getValue();
        assertThat(a.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(a.getMetric()).isEqualTo(AlarmMetric.PRICE);
        assertThat(a.getDirection()).isEqualTo(AlarmDirection.ABOVE);
        assertThat(a.getFrequency()).isEqualTo(AlarmFrequency.RECURRING);
    }

    @Test
    @DisplayName("create: blank userId → IllegalArgumentException; repo'ya gitmez")
    void create_blankUserId_rejected() {
        CreateAlarmRequest req = req("STOCK", "THYAO", "PRICE", "ABOVE", "300", null);

        assertThatThrownBy(() -> service.create("  ", EMAIL, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("create: geçersiz assetType → 'Invalid assetType'")
    void create_invalidAssetType_rejected() {
        CreateAlarmRequest req = req("WEIRD", "X", "PRICE", "ABOVE", "1", null);

        assertThatThrownBy(() -> service.create(USER, EMAIL, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid assetType");
    }

    @Test
    @DisplayName("create: blank symbol → 'symbol must not be blank'")
    void create_blankSymbol_rejected() {
        CreateAlarmRequest req = req("STOCK", "  ", "PRICE", "ABOVE", "1", null);

        assertThatThrownBy(() -> service.create(USER, EMAIL, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol");
    }

    @Test
    @DisplayName("create: null threshold → 'threshold must not be null'")
    void create_nullThreshold_rejected() {
        CreateAlarmRequest req = req("STOCK", "THYAO", "PRICE", "ABOVE", null, null);

        assertThatThrownBy(() -> service.create(USER, EMAIL, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    @DisplayName("create: geçersiz metric → 'Invalid metric'")
    void create_invalidMetric_rejected() {
        CreateAlarmRequest req = req("STOCK", "THYAO", "WEIRD", "ABOVE", "1", null);

        assertThatThrownBy(() -> service.create(USER, EMAIL, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid metric");
    }

    // ============================================================================
    // get / list
    // ============================================================================

    @Test
    @DisplayName("get: ID bulunamaz → ResourceNotFoundException")
    void get_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("list: repo'ya createdAt desc ile yönlendirilir")
    void list_callsRepo() {
        Alarm a1 = alarm("THYAO");
        Alarm a2 = alarm("BTC");
        when(repo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(a1, a2));

        List<Alarm> out = service.list(USER);

        assertThat(out).containsExactly(a1, a2);
    }

    // ============================================================================
    // update()
    // ============================================================================

    @Test
    @DisplayName("update: kısmi alanlar — yalnız verilen alanlar değişir, kalanlar korunur")
    void update_partialFields() {
        Alarm existing = alarm("THYAO");
        existing.setMetric(AlarmMetric.PRICE);
        existing.setDirection(AlarmDirection.ABOVE);
        existing.setThreshold(new BigDecimal("300"));
        UUID id = existing.getId();
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(existing));

        UpdateAlarmRequest req = new UpdateAlarmRequest();
        req.setThreshold(new BigDecimal("350"));
        // Yalnız threshold değişti

        Alarm out = service.update(USER, id, req);

        assertThat(out.getThreshold()).isEqualByComparingTo("350");
        assertThat(out.getMetric()).isEqualTo(AlarmMetric.PRICE);  // değişmedi
        assertThat(out.getDirection()).isEqualTo(AlarmDirection.ABOVE);
    }

    @Test
    @DisplayName("update: status=ACTIVE yapılırsa triggeredAt sıfırlanır")
    void update_reactivate_clearsTriggeredAt() {
        Alarm existing = alarm("THYAO");
        existing.setStatus(AlarmStatus.TRIGGERED);
        existing.setTriggeredAt(LocalDateTime.now());
        UUID id = existing.getId();
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(existing));

        UpdateAlarmRequest req = new UpdateAlarmRequest();
        req.setStatus("ACTIVE");

        Alarm out = service.update(USER, id, req);

        assertThat(out.getStatus()).isEqualTo(AlarmStatus.ACTIVE);
        assertThat(out.getTriggeredAt()).isNull();
    }

    // ============================================================================
    // delete()
    // ============================================================================

    @Test
    @DisplayName("delete: alarm silinir + audit log atılır")
    void delete_removesAndAudits() {
        Alarm existing = alarm("THYAO");
        UUID id = existing.getId();
        when(repo.findByIdAndUserId(id, USER)).thenReturn(Optional.of(existing));

        service.delete(USER, id);

        verify(repo).delete(existing);
        verify(businessLog).publish(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ============================================================================
    // disableAllForUser / reenableBanDisabledForUser (ban cascade)
    // ============================================================================

    @Test
    @DisplayName("disableAllForUser: ACTIVE → DISABLED + disabledByBan=true; pasif olanlara dokunulmaz")
    void disableAllForUser_marksOnlyActives() {
        Alarm active = alarm("A");
        active.setStatus(AlarmStatus.ACTIVE);
        Alarm alreadyDisabled = alarm("B");
        alreadyDisabled.setStatus(AlarmStatus.DISABLED);
        when(repo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(active, alreadyDisabled));

        int n = service.disableAllForUser(USER);

        assertThat(n).isEqualTo(1);
        assertThat(active.getStatus()).isEqualTo(AlarmStatus.DISABLED);
        assertThat(active.isDisabledByBan()).isTrue();
        // alreadyDisabled için save çağrılmamalı
        verify(repo, times(1)).save(any(Alarm.class));
    }

    @Test
    @DisplayName("disableAllForUser: blank userId → 0, repo'ya gitmez")
    void disableAllForUser_blankUserId_noOp() {
        assertThat(service.disableAllForUser("  ")).isZero();
        assertThat(service.disableAllForUser(null)).isZero();
        verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("reenableBanDisabledForUser: yalnız disabledByBan=true olanlar geri açılır")
    void reenableBanDisabledForUser_selectiveReactivation() {
        Alarm banDisabled = alarm("A");
        banDisabled.setStatus(AlarmStatus.DISABLED);
        banDisabled.setDisabledByBan(true);
        Alarm userDisabled = alarm("B");
        userDisabled.setStatus(AlarmStatus.DISABLED);
        userDisabled.setDisabledByBan(false);
        when(repo.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(banDisabled, userDisabled));

        int n = service.reenableBanDisabledForUser(USER);

        assertThat(n).isEqualTo(1);
        assertThat(banDisabled.getStatus()).isEqualTo(AlarmStatus.ACTIVE);
        assertThat(banDisabled.isDisabledByBan()).isFalse();
        assertThat(banDisabled.getTriggeredAt()).isNull();
        // userDisabled değişmedi
        assertThat(userDisabled.getStatus()).isEqualTo(AlarmStatus.DISABLED);
    }

    @Test
    @DisplayName("reenableBanDisabledForUser: blank userId → 0")
    void reenableBanDisabledForUser_blankUserId_noOp() {
        assertThat(service.reenableBanDisabledForUser("")).isZero();
        verifyNoInteractions(repo);
    }

    // ============================================================================
    // helpers
    // ============================================================================

    private static CreateAlarmRequest req(String type, String symbol, String metric,
                                          String direction, String threshold, String frequency) {
        CreateAlarmRequest r = new CreateAlarmRequest();
        r.setAssetType(type);
        r.setSymbol(symbol);
        r.setMetric(metric);
        r.setDirection(direction);
        r.setThreshold(threshold != null ? new BigDecimal(threshold) : null);
        r.setFrequency(frequency);
        return r;
    }

    private static Alarm alarm(String symbol) {
        Alarm a = new Alarm();
        a.setId(UUID.randomUUID());
        a.setUserId(USER);
        a.setSymbol(symbol);
        a.setAssetType(AssetType.STOCK);
        a.setStatus(AlarmStatus.ACTIVE);
        return a;
    }
}
