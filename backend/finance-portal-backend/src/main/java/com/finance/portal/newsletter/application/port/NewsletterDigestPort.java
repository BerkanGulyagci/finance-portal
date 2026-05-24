package com.finance.portal.newsletter.application.port;

import com.finance.portal.newsletter.application.model.DigestData;

/**
 * Bir kullanıcının bülten özetini (portföy + piyasa) toplayan port.
 * Implementasyon portföy ve ekonomi servislerini kullanır (infrastructure).
 */
public interface NewsletterDigestPort {

    DigestData buildFor(String userId);
}
