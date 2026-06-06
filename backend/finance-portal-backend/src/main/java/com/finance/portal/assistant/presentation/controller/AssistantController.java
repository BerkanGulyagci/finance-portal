package com.finance.portal.assistant.presentation.controller;

import com.finance.portal.assistant.application.AssistantProperties;
import com.finance.portal.assistant.application.AssistantService;
import com.finance.portal.assistant.application.AssistantService.AssistantReply;
import com.finance.portal.assistant.presentation.dto.AssistantChatRequest;
import com.finance.portal.assistant.presentation.dto.AssistantChatResponse;
import com.finance.portal.common.presentation.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Warren AI sohbet ucu. Public: giriş yapmayan kullanıcı {@code assistant.anon-message-limit} kadar mesaj
 * atabilir, sonra LOGIN_REQUIRED döner. JWT varsa kullanıcı günlük limiti uygulanır.
 * Sağlayıcı anahtarı backend'de kalır (frontend'e sızmaz).
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final AssistantProperties props;

    public AssistantController(AssistantService assistantService, AssistantProperties props) {
        this.assistantService = assistantService;
        this.props = props;
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AssistantChatResponse>> chat(
            @RequestBody AssistantChatRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {

        String userId = jwt != null ? jwt.getSubject() : null;
        String userName = jwt != null ? firstNonBlank(
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("preferred_username")) : null;
        String userEmail = jwt != null ? verifiedEmail(jwt) : null;
        String clientIp = clientIp(httpRequest, props.getTrustedProxyCount());

        AssistantReply reply = assistantService.chat(
                request != null ? request.messages() : null, userId, userName, userEmail, clientIp);

        AssistantChatResponse body = new AssistantChatResponse(reply.status().name(), reply.reply());
        return ResponseEntity.ok(ApiResponse.success(body, "Warren AI"));
    }

    private static String firstNonBlank(String... vals) {
        if (vals != null) {
            for (String v : vals) {
                if (v != null && !v.isBlank()) {
                    return v.trim();
                }
            }
        }
        return null;
    }

    /** Yalnızca doğrulanmış e-posta (alarm bildirimi için); doğrulanmamışsa null. */
    private static String verifiedEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            return null;
        }
        Object verified = jwt.getClaim("email_verified");
        boolean ok = (verified instanceof Boolean b && b)
                || (verified instanceof String s && Boolean.parseBoolean(s));
        return ok ? email : null;
    }

    /**
     * Anonim rate-limit için GERÇEK istemci IP'sini çıkarır.
     *
     * <p>GCP harici HTTP(S) yük dengeleyici (GKE GCE Ingress) X-Forwarded-For'u KORUR ve sona
     * {@code <client-ip>,<lb-ip>} EKLER; XFF'in başındaki (leftmost) girdiler istemci tarafından
     * gönderilmiş olabilir ve LB tarafından DOĞRULANMAZ. Bu yüzden gerçek istemci IP'si XFF'in
     * SONUNDAN {@code trustedProxyCount} kadar geri sayılan girdidir (LB'nin eklediği LB-IP atlanır).
     * Eskiden leftmost girdi alınıyordu → istemci her istekte farklı (sahte) bir leftmost IP
     * göndererek anonim sayacı her seferinde sıfırlayabiliyor, 2-mesaj limitini SINIRSIZ aşabiliyordu.</p>
     *
     * @param trustedProxyCount uygulama önündeki güvenilen proxy sayısı (GCLB için 1). XFF girdi
     *                          sayısından büyükse en soldaki bilinen girdiye düşülür.
     */
    static String clientIp(HttpServletRequest req, int trustedProxyCount) {
        if (req == null) {
            return null;
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            // Sondan trustedProxyCount kadar geri say (LB/proxy hop'larını atla); negatife düşmesin.
            int idx = parts.length - 1 - Math.max(0, trustedProxyCount);
            if (idx < 0) {
                idx = 0; // beklenenden az hop → en soldaki bilinen IP'ye düş
            }
            String ip = parts[idx].trim();
            if (!ip.isBlank()) {
                return ip;
            }
        }
        return req.getRemoteAddr();
    }
}
