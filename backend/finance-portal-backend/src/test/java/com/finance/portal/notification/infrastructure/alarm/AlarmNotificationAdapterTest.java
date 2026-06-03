package com.finance.portal.notification.infrastructure.alarm;

import com.finance.portal.alarm.application.model.AlarmTriggeredEvent;
import com.finance.portal.alarm.domain.AlarmDirection;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.common.domain.AssetType;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlarmNotificationAdapterTest {

    @Mock
    NotificationService notificationService;

    private AlarmNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AlarmNotificationAdapter(notificationService);
    }

    private static AlarmTriggeredEvent event(AssetType assetType, String symbol, String instrumentName,
                                             AlarmMetric metric, AlarmDirection direction,
                                             BigDecimal threshold, BigDecimal observed, String note) {
        return new AlarmTriggeredEvent(
                "user-1", "user@example.com", UUID.randomUUID(),
                assetType, symbol, instrumentName,
                metric, direction, threshold, observed, note);
    }

    /** Captures the 7 args passed to createAndSend so individual branches can be asserted. */
    private CapturedArgs captureSend() {
        ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<NotificationType> type = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> alarmId = ArgumentCaptor.forClass(UUID.class);

        verify(notificationService, times(1)).createAndSend(
                userId.capture(), type.capture(), title.capture(), body.capture(),
                html.capture(), email.capture(), alarmId.capture());

        CapturedArgs c = new CapturedArgs();
        c.userId = userId.getValue();
        c.type = type.getValue();
        c.title = title.getValue();
        c.body = body.getValue();
        c.html = html.getValue();
        c.email = email.getValue();
        c.alarmId = alarmId.getValue();
        return c;
    }

    private static final class CapturedArgs {
        String userId;
        NotificationType type;
        String title;
        String body;
        String html;
        String email;
        UUID alarmId;
    }

    @Test
    @DisplayName("PRICE/ABOVE: ALARM bildirimi, üzerine çıktı metni + para birimi sembolü")
    void priceAbove_happyPath() {
        AlarmTriggeredEvent e = event(AssetType.STOCK, "AAPL", "Apple Inc.",
                AlarmMetric.PRICE, AlarmDirection.ABOVE,
                new BigDecimal("150"), new BigDecimal("155.5"), null);

        adapter.notifyAlarmTriggered(e);

        CapturedArgs c = captureSend();
        assertThat(c.userId).isEqualTo("user-1");
        assertThat(c.type).isEqualTo(NotificationType.ALARM);
        assertThat(c.email).isEqualTo("user@example.com");
        assertThat(c.alarmId).isEqualTo(e.alarmId());
        // instrumentName tercih edildi
        assertThat(c.title).contains("Apple Inc.").contains("üzerine çıktı");
        // ABD hissesi → $ sembolü
        assertThat(c.body).contains("$").contains("Fiyat");
        // not yok → "Alarm Notunuz" geçmez
        assertThat(c.body).doesNotContain("Alarm Notunuz");
        assertThat(c.html).contains("Alarm Tetiklendi");
    }

    @Test
    @DisplayName("BELOW yönü: 'altına indi' metni kullanılır")
    void priceBelow_directionLabel() {
        AlarmTriggeredEvent e = event(AssetType.STOCK, "THYAO.IS", "Türk Hava Yolları",
                AlarmMetric.PRICE, AlarmDirection.BELOW,
                new BigDecimal("250"), new BigDecimal("240"), null);

        adapter.notifyAlarmTriggered(e);

        CapturedArgs c = captureSend();
        assertThat(c.title).contains("altına indi");
        // BIST hissesi (.IS) → ₺ sembolü
        assertThat(c.body).contains("₺");
        assertThat(c.type).isEqualTo(NotificationType.ALARM);
    }

    @Test
    @DisplayName("instrumentName boş ise sembole düşülür ve not eklenir")
    void blankInstrumentName_fallsBackToSymbolAndNote() {
        AlarmTriggeredEvent e = event(AssetType.CRYPTO, "BTC", "   ",
                AlarmMetric.PRICE, AlarmDirection.ABOVE,
                new BigDecimal("1000000"), new BigDecimal("1100000"), "  Dikkat et  ");

        adapter.notifyAlarmTriggered(e);

        CapturedArgs c = captureSend();
        // boş instrumentName → symbol gösterilir
        assertThat(c.title).contains("BTC");
        // not trim'lenip eklenir
        assertThat(c.body).contains("Alarm Notunuz: Dikkat et");
        assertThat(c.html).contains("Dikkat et");
        // kripto → ₺ sembolü
        assertThat(c.body).contains("₺");
    }

    @Test
    @DisplayName("MARGIN_RATIO: MARGIN_CALL bildirimi, yüzde gösterimi, para birimi yok")
    void marginRatio_marginCallBranch() {
        AlarmTriggeredEvent e = event(AssetType.FUTURE, "XU030", "VİOP Endeks",
                AlarmMetric.MARGIN_RATIO, AlarmDirection.BELOW,
                new BigDecimal("0.50"), new BigDecimal("0.42"), "Yön: LONG");

        adapter.notifyAlarmTriggered(e);

        CapturedArgs c = captureSend();
        assertThat(c.type).isEqualTo(NotificationType.MARGIN_CALL);
        assertThat(c.title).contains("Teminat Tehlikesi").contains("teminat oranı");
        // 0.50 → %50,00 (TR notation)
        assertThat(c.body).contains("%50,00");
        assertThat(c.body).contains("%42,00");
        // margin call HTML'i kırmızı banner
        assertThat(c.html).contains("Teminat Tehlikesi");
        assertThat(c.html).contains("Pozisyon Detayı");
    }

    @Test
    @DisplayName("CHANGE_PERCENT metriği yüzde ekiyle biçimlenir, etiket 'Günlük Değişim'")
    void changePercent_formatting() {
        AlarmTriggeredEvent e = event(AssetType.STOCK, "GARAN.IS", "Garanti",
                AlarmMetric.CHANGE_PERCENT, AlarmDirection.ABOVE,
                new BigDecimal("5"), new BigDecimal("6.25"), null);

        adapter.notifyAlarmTriggered(e);

        CapturedArgs c = captureSend();
        assertThat(c.body).contains("Günlük Değişim");
        assertThat(c.body).contains("5,00%");
        assertThat(c.body).contains("6,25%");
        assertThat(c.type).isEqualTo(NotificationType.ALARM);
    }

    @Test
    @DisplayName("VOLUME metriği tam sayıya yuvarlanır; null metric → 'Fiyat' etiketi")
    void volumeMetric_andNullValues() {
        AlarmTriggeredEvent e = event(AssetType.STOCK, "ASELS.IS", "Aselsan",
                AlarmMetric.VOLUME, AlarmDirection.ABOVE,
                new BigDecimal("1000000.7"), null, null);

        adapter.notifyAlarmTriggered(e);

        CapturedArgs c = captureSend();
        assertThat(c.body).contains("Hacim");
        // observedValue null → "-" döner
        assertThat(c.body).contains("-");
        assertThat(c.type).isEqualTo(NotificationType.ALARM);
        assertThat(c.title).isNotBlank();
    }

    @Test
    @DisplayName("HTML not alanı escape edilir (< > & karakterleri)")
    void htmlNoteIsEscaped() {
        AlarmTriggeredEvent e = event(AssetType.GOLD, "XAU", "Gram Altın",
                AlarmMetric.PRICE, AlarmDirection.ABOVE,
                new BigDecimal("2500"), new BigDecimal("2600"), "a < b & c > d");

        adapter.notifyAlarmTriggered(e);

        CapturedArgs c = captureSend();
        assertThat(c.html).contains("a &lt; b &amp; c &gt; d");
        // body ham haliyle notu içerir
        assertThat(c.body).contains("Alarm Notunuz: a < b & c > d");
    }

    @Test
    @DisplayName("BOND: para birimi sembolü yok (birimsiz gösterge değeri)")
    void bond_noCurrencySymbol() {
        AlarmTriggeredEvent e = event(AssetType.BOND, "TRT240227T17", "DİBS",
                AlarmMetric.PRICE, AlarmDirection.BELOW,
                new BigDecimal("90"), new BigDecimal("88.5"), null);

        adapter.notifyAlarmTriggered(e);

        CapturedArgs c = captureSend();
        assertThat(c.type).isEqualTo(NotificationType.ALARM);
        // BOND → "" sembol, fiyat sonuna $ veya ₺ eklenmez
        assertThat(c.body).doesNotContain("$");
        assertThat(c.body).doesNotContain("₺");
        verify(notificationService, times(1)).createAndSend(
                eq("user-1"), eq(NotificationType.ALARM),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), eq("user@example.com"), eq(e.alarmId()));
    }
}
