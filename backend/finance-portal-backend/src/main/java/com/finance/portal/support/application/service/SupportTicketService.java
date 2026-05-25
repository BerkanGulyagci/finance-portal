package com.finance.portal.support.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.common.application.exception.ResourceNotFoundException;
import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.notification.application.service.NotificationService;
import com.finance.portal.notification.domain.NotificationType;
import com.finance.portal.support.application.email.SupportEmailTemplate;
import com.finance.portal.support.domain.SupportTicket;
import com.finance.portal.support.domain.SupportTicketStatus;
import com.finance.portal.support.repository.SupportTicketRepository;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Destek talebi iş kuralları. Kullanıcı yalnız OPEN durumdaki talebini düzenleyebilir/silebilir.
 * Admin durumu değiştirince kullanıcıya uygulama-içi bildirim + e-posta gönderilir.
 */
@Service
public class SupportTicketService {

    private static final Logger log = LoggerFactory.getLogger(SupportTicketService.class);
    private static final int MAX_SUBJECT = 200;
    private static final int MAX_MESSAGE = 5000;
    private static final int MAX_ACTIVE_TICKETS = 3; // bir kullanıcının aynı anda açık tutabileceği talep sayısı
    private static final String ADMIN_ROLE = "ADMIN";

    private final SupportTicketRepository repository;
    private final NotificationService notificationService;
    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final CentralBusinessLogService businessLog;

    public SupportTicketService(SupportTicketRepository repository,
                                NotificationService notificationService,
                                KeycloakUserAdminPort keycloakUserAdminPort,
                                CentralBusinessLogService businessLog) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.keycloakUserAdminPort = keycloakUserAdminPort;
        this.businessLog = businessLog;
    }

    /** AUDIT iş logu — traceId/spanId otomatik eklenir, dashboard sayacı (fp.business.events) artar. */
    private void audit(String eventType, String action, String level, String result,
                       SupportTicket t, String actorUserId, Map<String, Object> extra) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("subject", t.getSubject());
        meta.put("status", t.getStatus() != null ? t.getStatus().name() : null);
        meta.put("ticketOwnerId", t.getUserId());
        if (extra != null) {
            meta.putAll(extra);
        }
        businessLog.publish(BusinessLogSupport.CATEGORY_AUDIT, eventType, level,
                "Support ticket " + action.toLowerCase() + ": " + t.getSubject(),
                BusinessLogSupport.ENTITY_SUPPORT_TICKET, t.getId() != null ? t.getId().toString() : null,
                action, result, meta, actorUserId, SupportTicketService.class.getName());
    }

    // ── Kullanıcı ─────────────────────────────────────────────────────────────
    @WithSpan("SupportTicketService.create")
    @Transactional
    public SupportTicket create(@SpanAttribute("support.user_id") String userId,
                                String userEmail, String reporterName, String subject, String message) {
        String s = clean(subject, MAX_SUBJECT);
        String m = clean(message, MAX_MESSAGE);
        if (s == null || m == null) {
            throw new IllegalArgumentException("Konu ve açıklama zorunludur.");
        }
        long active = repository.countByUserIdAndStatusNot(userId, SupportTicketStatus.RESOLVED);
        Span.current().setAttribute("support.active_tickets", active);
        if (active >= MAX_ACTIVE_TICKETS) {
            Span.current().setAttribute("support.limit_reached", true);
            throw new IllegalArgumentException(
                    "Aynı anda en fazla " + MAX_ACTIVE_TICKETS + " açık talebiniz olabilir. "
                            + "Mevcut talepleriniz çözülünce yenisini açabilirsiniz.");
        }
        SupportTicket t = new SupportTicket();
        t.setUserId(userId);
        t.setUserEmail(userEmail);
        t.setSubject(s);
        t.setMessage(m);
        t.setStatus(SupportTicketStatus.OPEN);
        SupportTicket saved = repository.save(t);
        Span.current().setAttribute("support.ticket_id", String.valueOf(saved.getId()));
        audit(BusinessLogSupport.EVENT_SUPPORT_TICKET_CREATED, BusinessLogSupport.ACTION_CREATE, "INFO",
                BusinessLogSupport.RESULT_SUCCESS, saved, userId, Map.of("activeTickets", active + 1));
        notifyAdmins(reporterName != null && !reporterName.isBlank() ? reporterName
                : (userEmail != null ? userEmail : "Bir kullanıcı"), saved);
        return saved;
    }

    @WithSpan("SupportTicketService.listForUser")
    @Transactional(readOnly = true)
    public List<SupportTicket> listForUser(@SpanAttribute("support.user_id") String userId) {
        List<SupportTicket> list = repository.findByUserIdOrderByCreatedAtDesc(userId);
        Span.current().setAttribute("support.ticket_count", list.size());
        return list;
    }

    @WithSpan("SupportTicketService.update")
    @Transactional
    public SupportTicket update(@SpanAttribute("support.user_id") String userId, UUID id, String subject, String message) {
        Span.current().setAttribute("support.ticket_id", String.valueOf(id));
        SupportTicket t = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Destek talebi bulunamadı: " + id));
        if (t.getStatus() != SupportTicketStatus.OPEN) {
            throw new IllegalArgumentException("Talep işleme alındıktan sonra düzenlenemez.");
        }
        String s = clean(subject, MAX_SUBJECT);
        String m = clean(message, MAX_MESSAGE);
        if (s == null || m == null) {
            throw new IllegalArgumentException("Konu ve açıklama zorunludur.");
        }
        t.setSubject(s);
        t.setMessage(m);
        SupportTicket saved = repository.save(t);
        audit(BusinessLogSupport.EVENT_SUPPORT_TICKET_UPDATED, BusinessLogSupport.ACTION_UPDATE, "INFO",
                BusinessLogSupport.RESULT_SUCCESS, saved, userId, null);
        return saved;
    }

    @WithSpan("SupportTicketService.delete")
    @Transactional
    public void delete(@SpanAttribute("support.user_id") String userId, UUID id) {
        Span.current().setAttribute("support.ticket_id", String.valueOf(id));
        SupportTicket t = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Destek talebi bulunamadı: " + id));
        if (t.getStatus() != SupportTicketStatus.OPEN) {
            throw new IllegalArgumentException("İşleme alınmış talep silinemez.");
        }
        repository.delete(t);
        audit(BusinessLogSupport.EVENT_SUPPORT_TICKET_DELETED, BusinessLogSupport.ACTION_DELETE, "INFO",
                BusinessLogSupport.RESULT_SUCCESS, t, userId, null);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────
    @WithSpan("SupportTicketService.listForUserAsAdmin")
    @Transactional(readOnly = true)
    public List<SupportTicket> listForUserAsAdmin(@SpanAttribute("support.user_id") String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @WithSpan("SupportTicketService.updateStatus")
    @Transactional
    public SupportTicket updateStatus(UUID id, SupportTicketStatus status, String adminNote) {
        Span.current().setAttribute("support.ticket_id", String.valueOf(id));
        SupportTicket t = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destek talebi bulunamadı: " + id));
        if (status == null) {
            throw new IllegalArgumentException("Geçersiz durum.");
        }
        SupportTicketStatus previous = t.getStatus();
        boolean statusChanged = previous != status;
        t.setStatus(status);
        if (adminNote != null) {
            t.setAdminNote(clean(adminNote, MAX_MESSAGE));
        }
        SupportTicket saved = repository.save(t);
        Span.current().setAttribute("support.status_from", previous != null ? previous.name() : "null");
        Span.current().setAttribute("support.status_to", status.name());
        Span.current().setAttribute("support.status_changed", statusChanged);
        // actor = işlemi yapan admin (AUDIT, actorUserId=null verince istek JWT'sinden çözülür)
        audit(BusinessLogSupport.EVENT_SUPPORT_TICKET_STATUS_CHANGED, BusinessLogSupport.ACTION_STATUS_CHANGE, "INFO",
                BusinessLogSupport.RESULT_SUCCESS, saved, null,
                Map.of("statusFrom", previous != null ? previous.name() : "null",
                        "statusTo", status.name(), "statusChanged", statusChanged));
        if (statusChanged) {
            notifyUser(saved);
        }
        return saved;
    }

    // ── Liste yardımcıları (admin) ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<String> userIdsWithActiveTickets() {
        return repository.findUserIdsWithActiveTickets();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> activeTicketCountByUser() {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : repository.countActiveGroupedByUser()) {
            map.put((String) row[0], (Long) row[1]);
        }
        return map;
    }

    @WithSpan("SupportTicketService.notifyUser")
    private void notifyUser(SupportTicket t) {
        Span.current().setAttribute("support.ticket_id", String.valueOf(t.getId()));
        Span.current().setAttribute("support.status_to", t.getStatus() != null ? t.getStatus().name() : "null");
        String title;
        String body;
        switch (t.getStatus()) {
            case IN_PROGRESS -> {
                title = "Destek talebiniz işleme alındı";
                body = "\"" + t.getSubject() + "\" başlıklı talebinizle ilgileniliyor.";
            }
            case RESOLVED -> {
                title = "Destek talebiniz çözüldü";
                body = "\"" + t.getSubject() + "\" başlıklı talebiniz çözüldü.";
            }
            default -> {
                title = "Destek talebiniz güncellendi";
                body = "\"" + t.getSubject() + "\" başlıklı talebinizin durumu: " + t.getStatus().getLabel() + ".";
            }
        }
        if (t.getAdminNote() != null && !t.getAdminNote().isBlank()) {
            body = body + " Not: " + t.getAdminNote();
        }
        notificationService.createAndSend(t.getUserId(), NotificationType.SUPPORT, title, body,
                SupportEmailTemplate.statusChanged(t), t.getUserEmail(), null);
    }

    /** Yeni talep açıldığında ADMIN rolündeki kullanıcılara yalnızca UYGULAMA-İÇİ bildirim (e-posta gönderilmez). */
    @WithSpan("SupportTicketService.notifyAdmins")
    private void notifyAdmins(String reporter, SupportTicket t) {
        int notified = 0;
        try {
            List<AdminUserView> admins = keycloakUserAdminPort.findUsersByRealmRole(ADMIN_ROLE);
            Span.current().setAttribute("support.admin_count", admins.size());
            String title = "Yeni destek talebi";
            String body = reporter + " kullanıcısından yeni bir talep: \"" + t.getSubject() + "\"";
            for (AdminUserView admin : admins) {
                if (admin.getId() == null || admin.getId().equals(t.getUserId())) {
                    continue; // talebi açan admin'in kendisine gönderme
                }
                // recipientEmail = null → NotificationService e-postayı atlar (SKIPPED), yalnız in-app bildirim oluşur.
                notificationService.createAndSend(admin.getId(), NotificationType.SUPPORT, title, body, null,
                        null, null);
                notified++;
            }
            Span.current().setAttribute("support.admins_notified", notified);
        } catch (Exception e) {
            Span.current().setAttribute("support.admins_notified", notified);
            Span.current().setAttribute("support.notify_error", String.valueOf(e.getMessage()));
            log.warn("Admin bildirimi gönderilemedi (talep {}): {}", t.getId(), e.getMessage());
        }
    }

    private static String clean(String s, int max) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
