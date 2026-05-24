package com.finance.portal.newsletter.application.service;

import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.newsletter.application.model.DigestData;
import com.finance.portal.newsletter.application.port.NewsletterDigestPort;
import com.finance.portal.newsletter.domain.NewsletterSubscription;
import com.finance.portal.newsletter.repository.NewsletterSubscriptionRepository;
import com.finance.portal.notification.application.port.EmailSenderPort;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seçilen sıklıkta (DAILY/WEEKLY/MONTHLY) kullanıcının dashboard özetini (portföy + piyasa)
 * "dashboard görünümlü" HTML e-posta olarak gönderen zamanlanmış iş. Her gün belirlenen
 * saatte çalışır; o gün gönderilmesi gereken abonelikleri bulup digest yollar.
 */
@Service
public class NewsletterDigestService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterDigestService.class);
    private static final Locale TR = new Locale("tr", "TR");
    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", TR);
    private static final DateTimeFormatter TX_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", TR);
    private static final String BRAND = "#093eaa";
    private static final String[] PALETTE = {
            "#093eaa", "#0ea5e9", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899", "#14b8a6"
    };

    private final NewsletterSubscriptionRepository repository;
    private final NewsletterDigestPort digestPort;
    private final EmailSenderPort emailSender;
    private final CentralBusinessLogService businessLog;
    private final NotificationService notificationService;
    private final boolean enabled;
    private final String baseUrl;

    public NewsletterDigestService(NewsletterSubscriptionRepository repository,
                                   NewsletterDigestPort digestPort,
                                   EmailSenderPort emailSender,
                                   CentralBusinessLogService businessLog,
                                   NotificationService notificationService,
                                   @Value("${newsletter.digest.enabled:true}") boolean enabled,
                                   @Value("${newsletter.base-url:http://localhost:8080}") String baseUrl) {
        this.repository = repository;
        this.digestPort = digestPort;
        this.emailSender = emailSender;
        this.businessLog = businessLog;
        this.notificationService = notificationService;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
    }

    @Scheduled(cron = "${newsletter.digest.cron:0 0 8 * * *}", zone = "Europe/Istanbul")
    public void sendDueDigests() {
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now(ZONE);
        List<NewsletterSubscription> subs = repository.findByEnabledTrue();
        for (NewsletterSubscription sub : subs) {
            if (sub.getEmail() == null || sub.getEmail().isBlank() || !isDue(sub, today)) {
                continue;
            }
            try {
                DigestData data = digestPort.buildFor(sub.getUserId());
                emailSender.send(sub.getEmail(), subject(today), render(data, sub, today));
                sub.setLastSentAt(LocalDateTime.now());
                repository.save(sub);
                log.info("Newsletter digest sent to user {} ({})", sub.getUserId(), sub.getFrequency());

                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("frequency", sub.getFrequency() != null ? sub.getFrequency().name() : null);
                businessLog.publish(BusinessLogSupport.CATEGORY_AUDIT, BusinessLogSupport.EVENT_NEWSLETTER_DIGEST_SENT,
                        "INFO", "Newsletter digest sent",
                        BusinessLogSupport.ENTITY_NEWSLETTER, sub.getId() != null ? sub.getId().toString() : null,
                        BusinessLogSupport.ACTION_SEND, BusinessLogSupport.RESULT_SUCCESS, meta,
                        sub.getUserId(), NewsletterDigestService.class.getName());

                // E-posta gönderildiğinde kullanıcıya uygulama-içi bildirim de düş (e-posta tekrarı olmasın diye
                // alıcı e-posta null → sadece in-app kayıt oluşturulur).
                notificationService.createAndSend(sub.getUserId(), NotificationType.NEWSLETTER,
                        "Bülten özetiniz gönderildi",
                        "Panonuzun " + frequencyLabel(sub) + " özeti e-posta adresinize gönderildi.",
                        null, null, null);
            } catch (Exception ex) {
                log.warn("Newsletter digest failed for user {}: {}", sub.getUserId(), ex.getMessage());
            }
        }
    }

    private boolean isDue(NewsletterSubscription sub, LocalDate today) {
        LocalDateTime last = sub.getLastSentAt();
        if (last != null && last.atZone(ZONE).toLocalDate().isEqual(today)) {
            return false;
        }
        return switch (sub.getFrequency()) {
            case DAILY -> true;
            case WEEKLY -> today.getDayOfWeek() == DayOfWeek.MONDAY;
            case MONTHLY -> today.getDayOfMonth() == 1;
        };
    }

    private String subject(LocalDate today) {
        return "FinansPortalı — Panonuzun Özeti (" + DATE_FMT.format(today) + ")";
    }

    // ── HTML render (dashboard görünümlü) ─────────────────────────────────────────
    private String render(DigestData d, NewsletterSubscription sub, LocalDate today) {
        String unsubscribe = baseUrl + "/api/newsletter/unsubscribe?token=" + sub.getUnsubscribeToken();
        String pnlColor = sign(d.totalProfitLoss());
        String dailyColor = sign(d.dailyChangePercent());

        StringBuilder b = new StringBuilder();
        b.append("<div style=\"background:#f3f4f6;padding:16px 0;\">");
        b.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:0 auto;color:#1a1b22;\">");

        // Header
        b.append("<div style=\"background:").append(BRAND).append(";padding:20px 24px;border-radius:12px 12px 0 0;\">")
                .append("<h1 style=\"color:#fff;margin:0;font-size:20px;\">FinansPortalı</h1>")
                .append("<p style=\"color:#dbe4f7;margin:4px 0 0;font-size:13px;\">Panonuzun Özeti · ")
                .append(DATE_FMT.format(today)).append("</p></div>");

        b.append("<div style=\"background:#f3f4f6;padding:14px;border-radius:0 0 12px 12px;\">");

        // İstatistik kutuları
        b.append("<table style=\"width:100%;border-collapse:separate;border-spacing:6px 0;\"><tr>");
        b.append(statTile("Toplam Değer", "&#8378;" + money(d.totalValue()), "#1a1b22"));
        b.append(statTile("Günlük Değişim", signedPct(d.dailyChangePercent()), dailyColor));
        b.append(statTile("Toplam K/Z", signedPct(d.totalProfitLossPercent()), pnlColor));
        b.append("</tr></table>");

        // Portföy Dağılımı
        if (!d.allocation().isEmpty()) {
            b.append(cardOpen("Portföy Dağılımı"));
            b.append(allocBar(d.allocation()));
            b.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;margin-top:10px;\">");
            int i = 0;
            for (DigestData.Slice s : d.allocation()) {
                b.append("<tr><td style=\"padding:3px 0;\"><span style=\"display:inline-block;width:10px;height:10px;border-radius:2px;background:")
                        .append(color(i++)).append(";\"></span>&nbsp; ").append(escape(s.label())).append("</td>")
                        .append("<td style=\"padding:3px 0;text-align:right;color:#6b7280;font-weight:bold;\">%")
                        .append(money1(s.percent())).append("</td></tr>");
            }
            b.append("</table>").append(cardClose());
        }

        // Portföylerim
        if (!d.portfolios().isEmpty()) {
            b.append(cardOpen("Portföylerim"));
            b.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;\">");
            for (DigestData.PortfolioLine p : d.portfolios()) {
                b.append("<tr><td style=\"padding:7px 0;border-top:1px solid #f3f4f6;\"><b>").append(escape(p.name()))
                        .append("</b><br><span style=\"color:").append(sign(p.profitLoss())).append(";font-size:12px;\">")
                        .append(signedMoney(p.profitLoss())).append(" (").append(signedPct(p.profitLossPercent())).append(")</span></td>")
                        .append("<td style=\"padding:7px 0;border-top:1px solid #f3f4f6;text-align:right;font-weight:bold;\">&#8378;")
                        .append(money(p.value())).append("</td></tr>");
            }
            b.append("</table>").append(cardClose());
        }

        // Favoriler
        if (!d.favorites().isEmpty()) {
            b.append(cardOpen("Favoriler"));
            b.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;\">");
            for (DigestData.Fav f : d.favorites()) b.append(favRow(f));
            b.append("</table>").append(cardClose());
        }

        // Öne Çıkanlar
        if (!d.gainers().isEmpty() || !d.losers().isEmpty()) {
            b.append(cardOpen("Öne Çıkanlar"));
            b.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;\">");
            for (DigestData.Mover m : d.gainers()) b.append(moverRow(m, "#059669", "&#9650;"));
            for (DigestData.Mover m : d.losers()) b.append(moverRow(m, "#dc2626", "&#9660;"));
            b.append("</table>").append(cardClose());
        }

        // Son İşlemler
        if (!d.recentTransactions().isEmpty()) {
            b.append(cardOpen("Son İşlemler"));
            b.append("<table style=\"width:100%;border-collapse:collapse;font-size:13px;\">");
            for (DigestData.TxLine tx : d.recentTransactions()) b.append(txRow(tx));
            b.append("</table>").append(cardClose());
        }

        // Piyasa
        DigestData.Market mk = d.market();
        b.append(cardOpen("Piyasa"));
        b.append("<table style=\"width:100%;border-collapse:collapse;font-size:14px;\">");
        if (mk.usdTry() != null) b.append(kv("Dolar/TL", "&#8378;" + money(mk.usdTry())));
        if (mk.bist100() != null) b.append(kv("BIST 100", num0(mk.bist100())));
        if (mk.gramAltin() != null) b.append(kv("Gram Altın", "&#8378;" + money(mk.gramAltin())));
        if (mk.inflationYoy() != null) b.append(kv("Enflasyon (TÜFE, yıllık)", "%" + money(mk.inflationYoy())));
        if (mk.policyRate() != null) b.append(kv("Politika Faizi", "%" + money(mk.policyRate())));
        b.append("</table>").append(cardClose());

        // CTA + footer
        b.append("<div style=\"text-align:center;margin:18px 0 4px;\"><a href=\"").append(baseUrl)
                .append("/dashboard\" style=\"display:inline-block;background:").append(BRAND)
                .append(";color:#fff;text-decoration:none;padding:11px 26px;border-radius:8px;font-weight:bold;font-size:14px;\">Panoyu Aç</a></div>");
        b.append("<p style=\"font-size:11px;color:#9ca3af;text-align:center;margin-top:14px;\">Bu özeti ")
                .append(frequencyLabel(sub)).append(" alıyorsunuz. <a href=\"").append(unsubscribe)
                .append("\" style=\"color:#9ca3af;\">Abonelikten çık</a></p>");

        b.append("</div></div></div>");
        return b.toString();
    }

    private static String statTile(String label, String value, String color) {
        return "<td style=\"width:33%;background:#fff;border:1px solid #ececec;border-radius:10px;padding:10px 6px;text-align:center;\">"
                + "<div style=\"font-size:10px;color:#9ca3af;text-transform:uppercase;letter-spacing:.5px;\">" + label + "</div>"
                + "<div style=\"font-size:15px;font-weight:bold;color:" + color + ";margin-top:3px;\">" + value + "</div></td>";
    }

    private static String cardOpen(String title) {
        return "<div style=\"background:#fff;border:1px solid #ececec;border-radius:12px;padding:16px;margin-top:12px;\">"
                + "<h2 style=\"font-size:15px;margin:0 0 10px;\">" + title + "</h2>";
    }

    private static String cardClose() {
        return "</div>";
    }

    private static String allocBar(List<DigestData.Slice> slices) {
        StringBuilder sb = new StringBuilder(
                "<table style=\"width:100%;border-collapse:collapse;border-radius:8px;overflow:hidden;\"><tr>");
        int i = 0;
        boolean any = false;
        for (DigestData.Slice s : slices) {
            double w = s.percent() == null ? 0 : s.percent().doubleValue();
            if (w <= 0) { i++; continue; }
            any = true;
            sb.append("<td style=\"background:").append(color(i++)).append(";height:16px;width:")
                    .append(String.format(Locale.US, "%.2f", w)).append("%;\"></td>");
        }
        if (!any) {
            sb.append("<td style=\"background:#e5e7eb;height:16px;\"></td>");
        }
        sb.append("</tr></table>");
        return sb.toString();
    }

    private static String kv(String k, String v) {
        return "<tr><td style=\"padding:6px 0;color:#6b7280;\">" + k
                + "</td><td style=\"padding:6px 0;text-align:right;font-weight:bold;\">" + v + "</td></tr>";
    }

    private static String moverRow(DigestData.Mover m, String color, String arrow) {
        return "<tr><td style=\"padding:5px 0;\"><b>" + escape(m.symbol()) + "</b> "
                + "<span style=\"color:#9ca3af;\">" + escape(m.name()) + "</span></td>"
                + "<td style=\"padding:5px 0;text-align:right;color:" + color + ";font-weight:bold;\">"
                + arrow + " " + signedPct(m.changePercent()) + "</td></tr>";
    }

    private static String favRow(DigestData.Fav f) {
        String price = f.lastPrice() != null ? "&#8378;" + money(f.lastPrice()) : "";
        String chg = f.changePercent() != null
                ? "<br><span style=\"color:" + sign(f.changePercent()) + ";font-size:12px;font-weight:bold;\">"
                  + signedPct(f.changePercent()) + "</span>"
                : "";
        String type = f.typeLabel() != null
                ? " <span style=\"color:#9ca3af;font-weight:normal;font-size:11px;\">" + escape(f.typeLabel()) + "</span>"
                : "";
        return "<tr><td style=\"padding:6px 0;border-top:1px solid #f3f4f6;\">&#9733; <b>" + escape(f.symbol()) + "</b>" + type + "</td>"
                + "<td style=\"padding:6px 0;border-top:1px solid #f3f4f6;text-align:right;font-weight:bold;\">" + price + chg + "</td></tr>";
    }

    private static String txRow(DigestData.TxLine tx) {
        boolean buy = "BUY".equalsIgnoreCase(tx.type());
        String c = buy ? "#059669" : "#dc2626";
        String label = buy ? "Alış" : "Satış";
        String date = tx.date() != null ? TX_DATE.format(tx.date()) : "";
        String pf = tx.portfolioName() != null ? escape(tx.portfolioName()) + " · " : "";
        return "<tr><td style=\"padding:6px 0;border-top:1px solid #f3f4f6;\"><b>" + escape(tx.symbol())
                + "</b> <span style=\"color:" + c + ";\">" + label + "</span>"
                + "<br><span style=\"color:#9ca3af;font-size:11px;\">" + pf + date + "</span></td>"
                + "<td style=\"padding:6px 0;border-top:1px solid #f3f4f6;text-align:right;font-weight:bold;\">&#8378;"
                + money(tx.total()) + "</td></tr>";
    }

    private String frequencyLabel(NewsletterSubscription sub) {
        return switch (sub.getFrequency()) {
            case DAILY -> "günlük";
            case WEEKLY -> "haftalık";
            case MONTHLY -> "aylık";
        };
    }

    private static String sign(BigDecimal v) {
        return (v == null || v.signum() >= 0) ? "#059669" : "#dc2626";
    }

    private static String money(BigDecimal v) {
        if (v == null) return "—";
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(v);
    }

    private static String money1(BigDecimal v) {
        if (v == null) return "—";
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        return nf.format(v);
    }

    private static String num0(BigDecimal v) {
        if (v == null) return "—";
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(0);
        return nf.format(v);
    }

    private static String signedMoney(BigDecimal v) {
        if (v == null) return "—";
        return (v.signum() >= 0 ? "+" : "") + "&#8378;" + money(v);
    }

    private static String signedPct(BigDecimal v) {
        if (v == null) return "—";
        return (v.signum() >= 0 ? "+" : "") + money(v) + "%";
    }

    private static String color(int i) {
        return PALETTE[Math.floorMod(i, PALETTE.length)];
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
