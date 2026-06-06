package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.exception.AdminPolicyException;
import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.admin.presentation.dto.BanType;
import com.finance.portal.admin.presentation.dto.BanUserRequest;
import com.finance.portal.alarm.application.service.AlarmService;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.newsletter.application.service.NewsletterService;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BanUserService {

    private static final Logger log = LoggerFactory.getLogger(BanUserService.class);
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String BRAND = "#093eaa";
    private static final DateTimeFormatter UNTIL_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", new Locale("tr", "TR"))
                    .withZone(ZoneId.of("Europe/Istanbul"));

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final UserBanStatePort userBanStatePort;
    private final UserAccountStatusPort userAccountStatusPort;
    private final CentralBusinessLogService centralBusinessLogService;
    private final AlarmService alarmService;
    private final NewsletterService newsletterService;
    private final NotificationService notificationService;

    @WithSpan("BanUserService.banUser")
    public void banUser(@SpanAttribute("admin.target_user_id") String targetUserId,
                        @SpanAttribute("admin.acting_user_id") String actingAdminUserId,
                        BanUserRequest request) {
        BanRequestValidator.validate(request);
        AdminUserView target = assertBanAllowed(targetUserId, actingAdminUserId);

        io.opentelemetry.api.trace.Span.current().setAttribute("admin.ban_type",
                request.getBanType() != null ? request.getBanType().name() : "UNKNOWN");

        String reason = request.getReason() != null ? request.getReason().trim() : null;
        boolean permanent = request.getBanType() == BanType.PERMANENT;
        Instant now = Instant.now();
        Instant banUntil = null;
        if (permanent) {
            userBanStatePort.save(new UserBanState(targetUserId, true, null, now, reason));
        } else {
            banUntil = BanDurationCalculator.calculateBanUntil(
                    request.getDurationValue(),
                    request.getDurationUnit()
            );
            userBanStatePort.save(new UserBanState(targetUserId, false, banUntil, now, reason));
        }

        keycloakUserAdminPort.setUserEnabled(targetUserId, false);
        keycloakUserAdminPort.logoutUserSessions(targetUserId);
        userAccountStatusPort.evictAccountStatus(targetUserId);

        // Banlanan kullanıcının alarmları ve bülten aboneliği pasifleşsin
        int disabledAlarms = alarmService.disableAllForUser(targetUserId);
        boolean newsletterDisabled = newsletterService.disableForUser(targetUserId);

        // Kullanıcıya "banlandınız + ban sebebi" e-postası ve uygulama-içi bildirim
        notifyBannedUser(targetUserId, target.getEmail(), reason, permanent, banUntil);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetUserId", targetUserId);
        metadata.put("adminUserId", actingAdminUserId);
        metadata.put("banType", request.getBanType().name());
        metadata.put("banReason", reason);
        metadata.put("disabledAlarms", disabledAlarms);
        metadata.put("newsletterDisabled", newsletterDisabled);
        if (!permanent) {
            metadata.put("durationValue", request.getDurationValue());
            metadata.put("durationUnit", request.getDurationUnit().name());
        }

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_USER_BANNED,
                "WARN",
                "User banned",
                "USER",
                targetUserId,
                BusinessLogSupport.ACTION_BAN,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                actingAdminUserId,
                BanUserService.class.getName()
        );
    }

    /** Ban e-postası + uygulama-içi bildirim. Hata olursa ban işlemini geri almaz. */
    private void notifyBannedUser(String userId, String email, String reason, boolean permanent, Instant banUntil) {
        String reasonText = (reason == null || reason.isBlank()) ? "Belirtilmedi" : reason;
        String durationText = permanent ? "kalıcı olarak" : "geçici olarak";
        String title = "Hesabınız banlandı";
        String body = "Hesabınız " + durationText + " askıya alındı. Ban sebebi: " + reasonText
                + (banUntil != null ? " (Bitiş: " + UNTIL_FMT.format(banUntil) + ")" : "");
        String html = buildBanEmailHtml(durationText, reasonText, banUntil);
        try {
            notificationService.createAndSend(userId, NotificationType.BAN, title, body, html, email, null);
        } catch (Exception ex) {
            log.warn("Ban bildirimi/e-postası gönderilemedi (userId={}): {}", userId, ex.getMessage());
        }
    }

    private String buildBanEmailHtml(String durationText, String reason, Instant banUntil) {
        StringBuilder b = new StringBuilder();
        b.append("<div style=\"background:#f3f4f6;padding:16px 0;\">");
        b.append("<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1a1b22;\">");
        b.append("<div style=\"background:").append(BRAND)
                .append(";padding:20px 24px;border-radius:12px 12px 0 0;\">")
                .append("<h1 style=\"color:#fff;margin:0;font-size:20px;\">Portiva</h1></div>");
        b.append("<div style=\"background:#fff;border:1px solid #ececec;border-top:none;")
                .append("padding:22px 24px;border-radius:0 0 12px 12px;\">");
        b.append("<h2 style=\"margin:0 0 10px;font-size:17px;color:#b91c1c;\">Hesabınız banlandı</h2>");
        b.append("<p style=\"margin:0 0 14px;font-size:14px;line-height:1.5;\">Hesabınız ")
                .append(durationText).append(" askıya alınmıştır.</p>");
        b.append("<div style=\"background:#fef2f2;border:1px solid #fecaca;border-radius:8px;")
                .append("padding:12px 14px;font-size:14px;\"><b>Ban sebebi:</b> ")
                .append(escape(reason)).append("</div>");
        if (banUntil != null) {
            b.append("<p style=\"margin:14px 0 0;font-size:13px;color:#6b7280;\">Ban bitiş tarihi: <b>")
                    .append(UNTIL_FMT.format(banUntil)).append("</b></p>");
        }
        b.append("<p style=\"margin:16px 0 0;font-size:12px;color:#9ca3af;\">İtirazınız için site yöneticisiyle iletişime geçebilirsiniz.</p>");
        b.append("</div></div></div>");
        return b.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private AdminUserView assertBanAllowed(String targetUserId, String actingAdminUserId) {
        if (targetUserId.equals(actingAdminUserId)) {
            throw new IllegalArgumentException("Kendi hesabınızı banlayamazsınız.");
        }

        AdminUserView target = keycloakUserAdminPort.getUser(targetUserId);
        if (hasAdminRole(target.getRoles())) {
            throw new AdminPolicyException("ADMIN rolüne sahip kullanıcılar banlanamaz.", HttpStatus.FORBIDDEN);
        }
        return target;
    }

    private static boolean hasAdminRole(List<String> roles) {
        return roles != null && roles.stream().anyMatch(ADMIN_ROLE::equals);
    }
}
