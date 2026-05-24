package com.finance.portal.newsletter.application.service;

import com.finance.portal.common.application.logging.BusinessLogSupport;
import com.finance.portal.common.application.logging.CentralBusinessLogService;
import com.finance.portal.newsletter.domain.NewsletterFrequency;
import com.finance.portal.newsletter.domain.NewsletterSubscription;
import com.finance.portal.newsletter.repository.NewsletterSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bülten aboneliği CRUD'u. Her kullanıcı için tek kayıt (user_id benzersiz) tutulur.
 */
@Service
public class NewsletterService {

    private final NewsletterSubscriptionRepository repository;
    private final CentralBusinessLogService businessLog;

    public NewsletterService(NewsletterSubscriptionRepository repository,
                             CentralBusinessLogService businessLog) {
        this.repository = repository;
        this.businessLog = businessLog;
    }

    private void audit(NewsletterSubscription sub, String eventType, String action) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("frequency", sub.getFrequency() != null ? sub.getFrequency().name() : null);
        meta.put("enabled", sub.isEnabled());
        businessLog.publish(BusinessLogSupport.CATEGORY_AUDIT, eventType, "INFO",
                "Newsletter " + action.toLowerCase(),
                BusinessLogSupport.ENTITY_NEWSLETTER, sub.getId() != null ? sub.getId().toString() : null,
                action, BusinessLogSupport.RESULT_SUCCESS, meta, sub.getUserId(),
                NewsletterService.class.getName());
    }

    @Transactional(readOnly = true)
    public Optional<NewsletterSubscription> findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    /** Aboneliği oluştur veya güncelle (frekans + açık/kapalı). */
    @Transactional
    public NewsletterSubscription upsert(String userId, String email,
                                         NewsletterFrequency frequency, boolean enabled) {
        NewsletterSubscription sub = repository.findByUserId(userId)
                .orElseGet(() -> {
                    NewsletterSubscription s = new NewsletterSubscription();
                    s.setUserId(userId);
                    return s;
                });
        if (email != null && !email.isBlank()) {
            sub.setEmail(email);
        }
        if (frequency != null) {
            sub.setFrequency(frequency);
        }
        sub.setEnabled(enabled);
        NewsletterSubscription saved = repository.save(sub);
        audit(saved,
                enabled ? BusinessLogSupport.EVENT_NEWSLETTER_SUBSCRIBED : BusinessLogSupport.EVENT_NEWSLETTER_UNSUBSCRIBED,
                enabled ? BusinessLogSupport.ACTION_SUBSCRIBE : BusinessLogSupport.ACTION_UNSUBSCRIBE);
        return saved;
    }

    /** Bir kullanıcının aboneliğini pasifleştirir (örn. kullanıcı banlandığında). Varsa true. */
    @Transactional
    public boolean disableForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return repository.findByUserId(userId).map(sub -> {
            if (!sub.isEnabled()) {
                return false;
            }
            sub.setEnabled(false);
            sub.setDisabledByBan(true);
            repository.save(sub);
            audit(sub, BusinessLogSupport.EVENT_NEWSLETTER_UNSUBSCRIBED, BusinessLogSupport.ACTION_UNSUBSCRIBE);
            return true;
        }).orElse(false);
    }

    /**
     * Ban kalkınca, YALNIZCA ban yüzünden pasifleştirilmiş aboneliği yeniden açar
     * (kullanıcı ban'dan önce kendi çıktıysa korunur). Açıldıysa true.
     */
    @Transactional
    public boolean reenableForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return repository.findByUserId(userId).map(sub -> {
            if (!sub.isDisabledByBan()) {
                return false;
            }
            sub.setEnabled(true);
            sub.setDisabledByBan(false);
            repository.save(sub);
            audit(sub, BusinessLogSupport.EVENT_NEWSLETTER_SUBSCRIBED, BusinessLogSupport.ACTION_SUBSCRIBE);
            return true;
        }).orElse(false);
    }

    /** E-posta linkinden token ile abonelikten çıkar. Bulunduysa true. */
    @Transactional
    public boolean unsubscribeByToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return repository.findByUnsubscribeToken(token).map(sub -> {
            sub.setEnabled(false);
            repository.save(sub);
            audit(sub, BusinessLogSupport.EVENT_NEWSLETTER_UNSUBSCRIBED, BusinessLogSupport.ACTION_UNSUBSCRIBE);
            return true;
        }).orElse(false);
    }
}
