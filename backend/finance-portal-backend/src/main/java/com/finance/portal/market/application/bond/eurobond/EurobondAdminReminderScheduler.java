package com.finance.portal.market.application.bond.eurobond;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HMB her ay (yaklaşık 20-23'ünde 17:30) "Merkezi Yönetim Dış Borç Stoku Tahvil Listesi"
 * xlsx'ini yayımlıyor. Filename'de her ay değişen hash + yıl/ay path olduğu için URL otomatik
 * tahmin edilemiyor; admin'in elle yüklemesi gerekiyor. Bu zamanlı görev publication penceresi
 * sonrası admin kullanıcılara hatırlatma bildirimi (+ email) gönderir.
 *
 * <p>Default cron: her ayın 21'i 09:00 (publication 20-23 aralığında olduğu için sonrasına denk gelir).
 * Override: {@code eurobond.refresh-reminder-cron} property.
 */
@Component
public class EurobondAdminReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(EurobondAdminReminderScheduler.class);

    private static final String HMB_PAGE_URL = "https://www.hmb.gov.tr/kamu-finansmani-istatistikleri";
    private static final String ADMIN_PATH = "/admin/eurobonds";

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final NotificationService notificationService;

    public EurobondAdminReminderScheduler(KeycloakUserAdminPort keycloakUserAdminPort,
                                          NotificationService notificationService) {
        this.keycloakUserAdminPort = keycloakUserAdminPort;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${eurobond.refresh-reminder-cron:0 0 9 21 * *}")
    @SchedulerLock(name = "eurobond-refresh-reminder", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    void sendMonthlyReminder() {
        List<AdminUserView> admins;
        try {
            admins = keycloakUserAdminPort.findUsersByRealmRole("ADMIN");
        } catch (Exception e) {
            log.warn("HMB hatırlatma: admin listesi alınamadı: {}", e.getMessage());
            return;
        }
        if (admins == null || admins.isEmpty()) {
            log.info("HMB hatırlatma: ADMIN rolünde kullanıcı bulunamadı, bildirim gönderilmedi.");
            return;
        }

        String title = "HMB Eurobond listesi yenilendi";
        String body = "HMB bu ay yeni \"Merkezi Yönetim Dış Borç Stoku Tahvil Listesi\" xlsx'ini "
                + "yayımlamış olmalı. Lütfen yeni linki Admin > Eurobondlar sayfasından yükleyin.";
        String emailHtml = buildEmailHtml();

        int sent = 0;
        for (AdminUserView a : admins) {
            if (a == null || a.getId() == null) {
                continue;
            }
            try {
                notificationService.createAndSend(
                        a.getId(),
                        NotificationType.ADMIN,
                        title,
                        body,
                        emailHtml,
                        a.getEmail(),
                        null);
                sent++;
            } catch (Exception e) {
                log.warn("HMB hatırlatma: admin {} için bildirim gönderilemedi: {}",
                        a.getUsername(), e.getMessage());
            }
        }
        log.info("HMB hatırlatma: {}/{} admin'e bildirim gönderildi.", sent, admins.size());
    }

    private static String buildEmailHtml() {
        return "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;\">"
                + "<h2 style=\"color:#1f2937;\">HMB Eurobond listesi yenilendi</h2>"
                + "<p>Hazine ve Maliye Bakanlığı (HMB) bu ay yeni "
                + "<strong>\"Merkezi Yönetim Dış Borç Stoku Tahvil Listesi\"</strong> xlsx'ini yayımlamış olmalı.</p>"
                + "<p>1) HMB sayfasından yeni xlsx linkini kopyalayın: "
                + "<a href=\"" + HMB_PAGE_URL + "\" target=\"_blank\">" + HMB_PAGE_URL + "</a></p>"
                + "<p>2) <strong>Admin > Eurobondlar</strong> (<code>" + ADMIN_PATH
                + "</code>) sayfasından xlsx URL'ini yapıştırıp yükleyin.</p>"
                + "<p style=\"color:#6b7280;font-size:12px;\">Bu otomatik bir hatırlatmadır — "
                + "her ayın 21'inde gönderilir.</p>"
                + "</div>";
    }
}
