package com.finance.portal.preferences.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.preferences.service.UserPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kullanıcı arayüz tercihleri — cihazlar arası senkron. Token yoksa (anonim) boş/no-op.
 */
@RestController
@RequestMapping(value = "/api/me/preferences", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserPreferenceController {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceController.class);

    private final UserPreferenceService service;

    public UserPreferenceController(UserPreferenceService service) {
        this.service = service;
    }

    /** Kullanıcının tüm tercihleri: { anahtar: değer(JSON) }. */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, JsonNode>>> getAll(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt != null ? jwt.getSubject() : null;
        Map<String, JsonNode> prefs = userId == null ? Map.of() : service.getAll(userId);
        return ResponseEntity.ok(ApiResponse.success(prefs, "Tercihler getirildi"));
    }

    /** Tek tercihi kaydet/güncelle (gövde = herhangi bir JSON değer). */
    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> put(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable String key,
                                                 @RequestBody(required = false) JsonNode value) {
        String userId = jwt != null ? jwt.getSubject() : null;
        if (userId != null) {
            try {
                service.upsert(userId, key, value);
            } catch (RuntimeException e) {
                // Sessiz client (axios .catch swallow) senaryosunda bile sunucu tarafında iz bırak.
                log.warn("Preference upsert failed: user={} key={} err={}", userId, key, e.toString());
                throw e;
            }
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Kaydedildi"));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String key) {
        String userId = jwt != null ? jwt.getSubject() : null;
        if (userId != null) {
            service.delete(userId, key);
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Silindi"));
    }
}
