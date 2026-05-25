package com.finance.portal.support.application.email;

import com.finance.portal.support.domain.SupportTicket;
import com.finance.portal.support.domain.SupportTicketStatus;

/**
 * Destek talebi e-postaları — genel FinansPortalı e-posta tasarımıyla (mavi başlık + kart) uyumlu HTML.
 */
public final class SupportEmailTemplate {

    private SupportEmailTemplate() {
    }

    /** Kullanıcıya: talep durumu değişti (hangi talep, yeni durum, admin notu). */
    public static String statusChanged(SupportTicket t) {
        String headline = switch (t.getStatus()) {
            case IN_PROGRESS -> "Talebiniz işleme alındı, ekibimiz ilgileniyor.";
            case RESOLVED -> "Talebiniz çözüldü.";
            default -> "Talebinizin durumu güncellendi.";
        };
        String noteBlock = (t.getAdminNote() != null && !t.getAdminNote().isBlank())
                ? "<div style=\"margin-top:12px;padding:12px;background:#eff6ff;border:1px solid #dbeafe;border-radius:10px;font-size:13px;color:#334155;white-space:pre-wrap\">"
                  + "<strong style=\"color:#093eaa\">Ekibimizin yanıtı:</strong><br>" + esc(t.getAdminNote()) + "</div>"
                : "";
        String content =
                "<p style=\"margin:0 0 16px;font-size:15px\">" + esc(headline) + "</p>"
                + "<table style=\"width:100%;border-collapse:collapse;font-size:14px\">"
                + row("Talep", "<strong>" + esc(t.getSubject()) + "</strong>")
                + row("Yeni durum", badge(t.getStatus()))
                + "</table>"
                + noteBlock
                + "<p style=\"margin:16px 0 0;font-size:12px;color:#94a3b8\">Talebinizi Profil &rsaquo; "
                + "\"Bir problem mi yaşıyorsunuz?\" bölümünden takip edebilirsiniz.</p>";
        return wrapper("🛟 FinansPortalı — Destek Talebi", content);
    }

    /** Admin'e: yeni talep geldi (kim, konu, mesaj). */
    public static String newTicketForAdmin(String reporter, SupportTicket t) {
        String content =
                "<p style=\"margin:0 0 16px;font-size:15px\"><strong>" + esc(reporter)
                + "</strong> kullanıcısından yeni bir destek talebi var.</p>"
                + "<table style=\"width:100%;border-collapse:collapse;font-size:14px\">"
                + row("Konu", "<strong>" + esc(t.getSubject()) + "</strong>")
                + "</table>"
                + "<div style=\"margin-top:12px;padding:12px;background:#f6f8fc;border-radius:10px;font-size:13px;color:#334155;white-space:pre-wrap\">"
                + esc(t.getMessage()) + "</div>"
                + "<p style=\"margin:16px 0 0;font-size:12px;color:#94a3b8\">Admin Panel &rsaquo; kullanıcı detayından durumu güncelleyebilirsiniz.</p>";
        return wrapper("🆕 FinansPortalı — Yeni Destek Talebi", content);
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:8px 0;color:#64748b;vertical-align:top\">" + esc(label)
                + "</td><td style=\"padding:8px 0;text-align:right\">" + value + "</td></tr>";
    }

    private static String badge(SupportTicketStatus s) {
        String color = switch (s) {
            case RESOLVED -> "#10b981";
            case IN_PROGRESS -> "#0ea5e9";
            default -> "#f59e0b";
        };
        return "<span style=\"display:inline-block;padding:3px 10px;border-radius:999px;background:" + color
                + ";color:#fff;font-size:12px;font-weight:bold\">" + esc(s.getLabel()) + "</span>";
    }

    private static String wrapper(String header, String content) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden\">"
                + "<div style=\"background:#093eaa;color:#fff;padding:18px 24px;font-size:18px;font-weight:bold\">" + header + "</div>"
                + "<div style=\"padding:24px;color:#1a1c1e\">" + content + "</div>"
                + "<div style=\"padding:14px 24px;background:#f6f8fc;color:#94a3b8;font-size:12px\">Bu e-posta FinansPortalı destek sisteminden otomatik gönderilmiştir.</div>"
                + "</div>";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
