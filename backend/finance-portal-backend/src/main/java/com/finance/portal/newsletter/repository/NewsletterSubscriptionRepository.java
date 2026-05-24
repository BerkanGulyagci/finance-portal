package com.finance.portal.newsletter.repository;

import com.finance.portal.newsletter.domain.NewsletterSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscription, UUID> {

    Optional<NewsletterSubscription> findByUserId(String userId);

    Optional<NewsletterSubscription> findByUnsubscribeToken(String unsubscribeToken);

    List<NewsletterSubscription> findByEnabledTrue();
}
