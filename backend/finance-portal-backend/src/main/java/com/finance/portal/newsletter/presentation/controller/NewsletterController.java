package com.finance.portal.newsletter.presentation.controller;

import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.newsletter.application.service.NewsletterService;
import com.finance.portal.newsletter.domain.NewsletterFrequency;
import com.finance.portal.newsletter.domain.NewsletterSubscription;
import com.finance.portal.newsletter.presentation.dto.NewsletterSubscriptionResponse;
import com.finance.portal.newsletter.presentation.dto.UpdateNewsletterRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/newsletter")
public class NewsletterController {

    private final NewsletterService newsletterService;

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    /** Mevcut abonelik durumu (yoksa abone değil + varsayılan frekans). */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<NewsletterSubscriptionResponse>> getMine(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        NewsletterSubscriptionResponse body = newsletterService.findByUserId(userId)
                .map(NewsletterController::toResponse)
                .orElseGet(() -> new NewsletterSubscriptionResponse(false,
                        NewsletterFrequency.WEEKLY.name(), jwt.getClaimAsString("email")));
        return ResponseEntity.ok(ApiResponse.success(body, "Newsletter subscription retrieved"));
    }

    /** Aboneliği oluştur/güncelle (frekans + açık/kapalı). */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<NewsletterSubscriptionResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateNewsletterRequest request) {
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        NewsletterSubscription saved = newsletterService.upsert(
                userId, email, request.getFrequency(), request.isEnabled());
        return ResponseEntity.ok(ApiResponse.success(toResponse(saved), "Newsletter subscription updated"));
    }

    /** E-postadaki linkten abonelikten çık (kimlik doğrulamasız, token ile). */
    @GetMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribe(@RequestParam("token") String token) {
        boolean ok = newsletterService.unsubscribeByToken(token);
        String message = ok
                ? "Bülten aboneliğiniz iptal edildi. Dilediğinizde Profil ayarlarından tekrar abone olabilirsiniz."
                : "Bağlantı geçersiz veya süresi dolmuş.";
        String html = "<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Bülten Aboneliği</title></head>"
                + "<body style=\"font-family:Arial,sans-serif;background:#f3f4f6;margin:0;padding:48px 16px;\">"
                + "<div style=\"max-width:480px;margin:0 auto;background:#fff;border-radius:12px;padding:32px;text-align:center;\">"
                + "<h1 style=\"color:#093eaa;font-size:20px;margin:0 0 12px;\">FinansPortalı</h1>"
                + "<p style=\"color:#374151;font-size:14px;\">" + message + "</p></div></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private static NewsletterSubscriptionResponse toResponse(NewsletterSubscription s) {
        return new NewsletterSubscriptionResponse(s.isEnabled(), s.getFrequency().name(), s.getEmail());
    }
}
