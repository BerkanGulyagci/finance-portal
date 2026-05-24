package com.finance.portal.newsletter.presentation.dto;

/**
 * Kullanıcının bülten aboneliği durumu.
 * subscribed=false ise kullanıcı henüz abone değildir (frequency varsayılanı önerilir).
 */
public record NewsletterSubscriptionResponse(
        boolean subscribed,
        String frequency,
        String email
) {}
