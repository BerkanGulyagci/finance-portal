package com.finance.portal.notification.application.service;

import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.notification.application.port.EmailSenderPort;
import com.finance.portal.notification.domain.EmailStatus;
import com.finance.portal.notification.domain.Notification;
import com.finance.portal.notification.domain.NotificationType;
import com.finance.portal.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uygulama-içi bildirim üretimi + okuma/işaretleme. Bildirim oluşturulurken (alıcı e-posta varsa)
 * eşzamanlı olarak e-posta da gönderilir ve gönderim durumu bildirim üzerinde saklanır.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_LIST = 100;

    private final NotificationRepository notificationRepository;
    private final EmailSenderPort emailSenderPort;
    private final CentralBusinessLogService businessLog;

    public NotificationService(NotificationRepository notificationRepository,
                               EmailSenderPort emailSenderPort,
                               CentralBusinessLogService businessLog) {
        this.notificationRepository = notificationRepository;
        this.emailSenderPort = emailSenderPort;
        this.businessLog = businessLog;
    }

    /**
     * Bildirimi kaydeder ve (alıcı e-posta varsa) e-posta gönderir.
     *
     * @param body      uygulama-içi gösterilecek düz metin gövde
     * @param emailHtml e-posta için HTML gövde (null ise body kullanılır)
     */
    @Transactional
    public Notification createAndSend(String userId, NotificationType type, String title, String body,
                                      String emailHtml, String recipientEmail, UUID relatedAlarmId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type != null ? type : NotificationType.ALARM);
        n.setTitle(title);
        n.setBody(body);
        n.setRelatedAlarmId(relatedAlarmId);
        n.setRecipientEmail(recipientEmail);
        n.setRead(false);

        if (recipientEmail == null || recipientEmail.isBlank()) {
            n.setEmailStatus(EmailStatus.SKIPPED);
            n.setEmailError("Alıcı e-posta yok veya doğrulanmamış");
        } else {
            try {
                emailSenderPort.send(recipientEmail, title, emailHtml != null ? emailHtml : body);
                n.setEmailStatus(EmailStatus.SENT);
                n.setSentAt(LocalDateTime.now());
            } catch (Exception ex) {
                n.setEmailStatus(EmailStatus.FAILED);
                n.setEmailError(truncate(ex.getMessage(), 1000));
                log.warn("Notification e-mail failed for user {}: {}", userId, ex.getMessage());
            }
        }
        Notification saved = notificationRepository.save(n);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("type", saved.getType() != null ? saved.getType().name() : null);
        meta.put("emailStatus", saved.getEmailStatus() != null ? saved.getEmailStatus().name() : null);
        businessLog.publish(BusinessLogSupport.CATEGORY_AUDIT, BusinessLogSupport.EVENT_NOTIFICATION_CREATED, "INFO",
                "Notification created: " + title,
                BusinessLogSupport.ENTITY_NOTIFICATION, saved.getId() != null ? saved.getId().toString() : null,
                BusinessLogSupport.ACTION_CREATE,
                saved.getEmailStatus() == EmailStatus.FAILED ? BusinessLogSupport.RESULT_FAILURE : BusinessLogSupport.RESULT_SUCCESS,
                meta, userId, NotificationService.class.getName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Notification> getUserNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_LIST));
    }

    @Transactional(readOnly = true)
    public long countUnread(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public Notification markRead(String userId, UUID id) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (!n.isRead()) {
            n.setRead(true);
            notificationRepository.save(n);
        }
        return n;
    }

    @Transactional
    public int markAllRead(String userId) {
        return notificationRepository.markAllReadForUser(userId);
    }

    @Transactional
    public void delete(String userId, UUID id) {
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        notificationRepository.delete(n);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
