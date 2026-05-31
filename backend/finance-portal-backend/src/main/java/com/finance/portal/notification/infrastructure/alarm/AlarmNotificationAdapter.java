package com.finance.portal.notification.infrastructure.alarm;

import com.finance.portal.alarm.application.model.AlarmTriggeredEvent;
import com.finance.portal.alarm.application.port.AlarmNotificationPort;
import com.finance.portal.alarm.domain.AlarmCurrency;
import com.finance.portal.alarm.domain.AlarmDirection;
import com.finance.portal.alarm.domain.AlarmMetric;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Alarm tetiklendiğinde uygulama-içi bildirim + e-posta üretir. Eşik/değer, enstrümanın
 * para birimi sembolüyle ({@link AlarmCurrency}) gösterilir; kullanıcı not bıraktıysa
 * "Alarm Notunuz:" olarak bildirime/maile eklenir.
 */
@Component
public class AlarmNotificationAdapter implements AlarmNotificationPort {

    private static final Locale TR = new Locale("tr", "TR");

    private final NotificationService notificationService;

    public AlarmNotificationAdapter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void notifyAlarmTriggered(AlarmTriggeredEvent e) {
        String instrument = (e.instrumentName() != null && !e.instrumentName().isBlank())
                ? e.instrumentName() : e.symbol();
        // MARGIN_RATIO için para birimi sembolü anlamsız — formatValue zaten "%" döner.
        String cur = e.metric() == AlarmMetric.MARGIN_RATIO ? null
                : AlarmCurrency.symbolFor(e.assetType(), e.symbol());
        String metricLabel = metricLabel(e.metric());
        String directionLabel = e.direction() == AlarmDirection.ABOVE ? "üzerine çıktı" : "altına indi";
        String thresholdStr = formatValue(e.metric(), e.threshold(), cur);
        String observedStr = formatValue(e.metric(), e.observedValue(), cur);
        String note = (e.note() != null && !e.note().isBlank()) ? e.note().trim() : null;

        boolean isMargin = e.metric() == AlarmMetric.MARGIN_RATIO;

        // Margin call başlığı vurgulu: gerçek brokerda teminat tamamlama çağrısı gelir.
        String title = isMargin
                ? String.format("Teminat Tehlikesi — %s teminat oranı %s'nin altına indi", instrument, thresholdStr)
                : String.format("Alarm: %s değeri %s'nin %s", instrument, thresholdStr, directionLabel);

        StringBuilder body = new StringBuilder(isMargin
                ? String.format(
                        "%s pozisyonunuzun teminat oranı %s eşiğinin altına indi. Şu anki oran: %s. "
                                + "Gerçek brokerda bu seviyede teminat tamamlama (margin call) çağrısı gelir.",
                        instrument, thresholdStr, observedStr)
                : String.format(
                        "%s için kurduğunuz alarm tetiklendi: %s %s %s. Şu anki değer: %s.",
                        instrument, metricLabel, thresholdStr, directionLabel, observedStr));
        if (note != null) {
            body.append(" Alarm Notunuz: ").append(note);
        }

        String html = isMargin
                ? buildMarginCallHtml(instrument, e.symbol(), thresholdStr, observedStr, note)
                : buildHtml(instrument, e.symbol(), metricLabel, directionLabel, thresholdStr, observedStr, note);

        NotificationType nt = isMargin ? NotificationType.MARGIN_CALL : NotificationType.ALARM;
        notificationService.createAndSend(
                e.userId(), nt, title, body.toString(), html, e.recipientEmail(), e.alarmId());
    }

    private static String metricLabel(AlarmMetric metric) {
        if (metric == null) {
            return "Fiyat";
        }
        return switch (metric) {
            case PRICE -> "Fiyat";
            case CHANGE_PERCENT -> "Günlük Değişim";
            case VOLUME -> "Hacim";
            case MARGIN_RATIO -> "Teminat Oranı";
        };
    }

    /** Değeri TR biçiminde + (fiyatsa) para birimi sembolüyle döndürür. */
    private static String formatValue(AlarmMetric metric, BigDecimal value, String cur) {
        if (value == null) {
            return "-";
        }
        DecimalFormatSymbols sym = new DecimalFormatSymbols(TR);
        if (metric == AlarmMetric.CHANGE_PERCENT) {
            return new DecimalFormat("#,##0.00", sym).format(value) + "%";
        }
        if (metric == AlarmMetric.MARGIN_RATIO) {
            // 0.50 → "%50,00" (TR notation, virgül ondalık). value zaten 0–1 ölçeği.
            BigDecimal pct = value.multiply(new BigDecimal("100"));
            return "%" + new DecimalFormat("#,##0.00", sym).format(pct);
        }
        if (metric == AlarmMetric.VOLUME) {
            return new DecimalFormat("#,##0", sym).format(value.setScale(0, RoundingMode.HALF_UP));
        }
        String n = new DecimalFormat("#,##0.######", sym).format(value);
        return (cur != null && !cur.isBlank()) ? n + " " + cur : n;
    }

    private static String buildHtml(String instrument, String symbol, String metricLabel,
                                    String directionLabel, String thresholdStr, String observedStr, String note) {
        String noteRow = note == null ? "" : String.format(
                "<tr><td style=\"padding:8px 0;color:#64748b\">Alarm Notunuz</td>"
                        + "<td style=\"padding:8px 0;text-align:right;font-weight:bold\">%s</td></tr>",
                escape(note));
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden">
                  <div style="background:#093eaa;color:#fff;padding:18px 24px;font-size:18px;font-weight:bold">🔔 FinansPortalı — Alarm Tetiklendi</div>
                  <div style="padding:24px;color:#1a1c1e">
                    <p style="margin:0 0 12px"><strong>%s</strong> <span style="color:#64748b">(%s)</span> için kurduğunuz alarm tetiklendi.</p>
                    <table style="width:100%%;border-collapse:collapse;font-size:14px">
                      <tr><td style="padding:8px 0;color:#64748b">Metrik</td><td style="padding:8px 0;text-align:right;font-weight:bold">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Koşul</td><td style="padding:8px 0;text-align:right;font-weight:bold">%s %s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Şu anki değer</td><td style="padding:8px 0;text-align:right;font-weight:bold;color:#093eaa">%s</td></tr>
                      %s
                    </table>
                  </div>
                  <div style="padding:14px 24px;background:#f6f8fc;color:#94a3b8;font-size:12px">Bu e-posta FinansPortalı alarm sisteminden otomatik gönderilmiştir.</div>
                </div>
                """.formatted(instrument, symbol, metricLabel, thresholdStr, directionLabel, observedStr, noteRow);
    }

    /**
     * VİOP teminat-uyarısı maili. Kırmızı banner + "gerçek brokerda margin call gelirdi" açıklama.
     * Pozisyon detayları (yön, miktar, giriş/güncel fiyat, teminat, açık K/Z) — varsa MarginAlarmEvaluator
     * tarafından event.note üzerinden geçirilir; burada salt teminat oranı + eşik gösterilir.
     */
    private static String buildMarginCallHtml(String instrument, String symbol, String thresholdStr,
                                              String observedStr, String note) {
        String noteRow = note == null ? "" : String.format(
                "<tr><td style=\"padding:8px 0;color:#64748b\">Pozisyon Detayı</td>"
                        + "<td style=\"padding:8px 0;text-align:right;font-weight:bold\">%s</td></tr>",
                escape(note));
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;border:1px solid #fecaca;border-radius:16px;overflow:hidden">
                  <div style="background:#dc2626;color:#fff;padding:18px 24px;font-size:18px;font-weight:bold">⚠️ FinansPortalı — Teminat Tehlikesi</div>
                  <div style="padding:24px;color:#1a1c1e">
                    <p style="margin:0 0 12px"><strong>%s</strong> <span style="color:#64748b">(%s)</span> pozisyonunuzun teminat oranı kritik eşiğin altına indi.</p>
                    <table style="width:100%%;border-collapse:collapse;font-size:14px">
                      <tr><td style="padding:8px 0;color:#64748b">Eşik</td><td style="padding:8px 0;text-align:right;font-weight:bold">%s</td></tr>
                      <tr><td style="padding:8px 0;color:#64748b">Şu anki teminat oranı</td><td style="padding:8px 0;text-align:right;font-weight:bold;color:#dc2626">%s</td></tr>
                      %s
                    </table>
                    <p style="margin:18px 0 0;padding:14px;background:#fef2f2;border-left:4px solid #dc2626;color:#7f1d1d;font-size:13px;line-height:1.5">
                      <strong>Uyarı:</strong> Gerçek brokerlerde bu seviyede <strong>teminat tamamlama çağrısı (margin call)</strong> gelir.
                      Pozisyonunuzu kapatmanız veya teminat eklemeniz gerekebilir; aksi halde broker pozisyonunuzu zorunlu kapatabilir.
                    </p>
                  </div>
                  <div style="padding:14px 24px;background:#fef2f2;color:#94a3b8;font-size:12px">Bu e-posta FinansPortalı VİOP teminat alarm sisteminden otomatik gönderilmiştir.</div>
                </div>
                """.formatted(instrument, symbol, thresholdStr, observedStr, noteRow);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
