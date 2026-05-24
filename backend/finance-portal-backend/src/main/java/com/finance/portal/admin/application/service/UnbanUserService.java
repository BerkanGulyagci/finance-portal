package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.alarm.application.service.AlarmService;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import com.finance.portal.newsletter.application.service.NewsletterService;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnbanUserService {

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final UserBanStatePort userBanStatePort;
    private final UserAccountStatusPort userAccountStatusPort;
    private final CentralBusinessLogService centralBusinessLogService;
    private final AlarmService alarmService;
    private final NewsletterService newsletterService;

    public void unbanUser(String userId) {
        unbanUser(userId, null);
    }

    @WithSpan("UnbanUserService.unbanUser")
    public void unbanUser(@SpanAttribute("admin.target_user_id") String userId,
                          @SpanAttribute("admin.acting_user_id") String actingAdminUserId) {
        keycloakUserAdminPort.getUser(userId);
        userBanStatePort.deleteByUserId(userId);
        keycloakUserAdminPort.setUserEnabled(userId, true);
        userAccountStatusPort.evictAccountStatus(userId);

        // Ban yüzünden pasifleştirilen alarm ve aboneliği geri aç (elle kapatılanlar korunur)
        int reenabledAlarms = alarmService.reenableBanDisabledForUser(userId);
        boolean newsletterReenabled = newsletterService.reenableForUser(userId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetUserId", userId);
        metadata.put("reenabledAlarms", reenabledAlarms);
        metadata.put("newsletterReenabled", newsletterReenabled);
        if (actingAdminUserId != null) {
            metadata.put("adminUserId", actingAdminUserId);
        }

        centralBusinessLogService.publish(
                BusinessLogSupport.CATEGORY_AUDIT,
                BusinessLogSupport.EVENT_USER_UNBANNED,
                "INFO",
                "User unbanned",
                "USER",
                userId,
                BusinessLogSupport.ACTION_UNBAN,
                BusinessLogSupport.RESULT_SUCCESS,
                metadata,
                actingAdminUserId,
                UnbanUserService.class.getName()
        );
    }
}
