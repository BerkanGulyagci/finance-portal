package com.finance.portal.preferences.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.finance.portal.preferences.domain.UserMarginAlertSetting;
import com.finance.portal.preferences.domain.UserPreference;
import com.finance.portal.preferences.repository.UserMarginAlertSettingRepository;
import com.finance.portal.preferences.repository.UserPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kullanıcı arayüz tercihleri (anahtar-değer JSON). value veritabanında JSON metni olarak tutulur;
 * okuma sırasında JsonNode'a çözülür ki API gerçek JSON döndürsün.
 *
 * <p>VİOP teminat-uyarı eşiği ({@code marginAlertThresholdPct}) ayrı bir tabloda
 * ({@link UserMarginAlertSetting}) tutulur; {@link #getAll} cevabında geriye uyumlu
 * şekilde {@code margin_alert_threshold_pct} anahtarıyla harmanlanır.
 */
@Service
public class UserPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceService.class);

    /** /api/me/preferences cevabında ve PUT yolunda kullanılan birleştirilmiş anahtar. */
    public static final String KEY_MARGIN_ALERT_THRESHOLD_PCT = "margin_alert_threshold_pct";

    private final UserPreferenceRepository repository;
    private final UserMarginAlertSettingRepository marginAlertRepository;
    private final ObjectMapper objectMapper;

    public UserPreferenceService(UserPreferenceRepository repository,
                                 UserMarginAlertSettingRepository marginAlertRepository,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.marginAlertRepository = marginAlertRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, JsonNode> getAll(String userId) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        for (UserPreference p : repository.findByUserId(userId)) {
            if (p.getValue() == null || p.getValue().isBlank()) {
                continue;
            }
            try {
                out.put(p.getPrefKey(), objectMapper.readTree(p.getValue()));
            } catch (Exception e) {
                log.debug("Bozuk tercih JSON atlandı [{}]: {}", p.getPrefKey(), e.getMessage());
            }
        }
        // Adanmış tabloda tutulan VİOP teminat-uyarı eşiği — frontend prefs sözlüğüne enjekte et.
        out.put(KEY_MARGIN_ALERT_THRESHOLD_PCT, IntNode.valueOf(getMarginAlertThresholdPct(userId)));
        return out;
    }

    /**
     * Kullanıcının VİOP teminat-uyarı eşiği (0–100). Kayıt yoksa varsayılan 50 döner.
     * 0 = uyarı kapalı (scanner kullanıcıyı atlar).
     */
    @Transactional(readOnly = true)
    public int getMarginAlertThresholdPct(String userId) {
        if (userId == null || userId.isBlank()) {
            return 50;
        }
        return marginAlertRepository.findByUserId(userId)
                .map(UserMarginAlertSetting::getMarginAlertThresholdPct)
                .map(v -> clamp(v, 0, 100))
                .orElse(50);
    }

    /** {@link #upsert} entegrasyonu için kullanılan dahili yardımcı; UI'dan gelen değeri 0–100'e kıstırır. */
    @Transactional
    public void setMarginAlertThresholdPct(String userId, int value) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        int clamped = clamp(value, 0, 100);
        UserMarginAlertSetting setting = marginAlertRepository.findByUserId(userId)
                .orElseGet(() -> new UserMarginAlertSetting(userId, clamped));
        setting.setMarginAlertThresholdPct(clamped);
        marginAlertRepository.save(setting);
        // Future debugging hook: row never written ⇒ scanner düşer 0.5000 (50% default) — bu log
        // hangi user için hangi yüzde DB'ye yazıldı görmek için ana referans.
        log.info("Margin threshold persisted: user={} value={}", userId, clamped);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Transactional
    public void upsert(String userId, String key, JsonNode value) {
        if (key == null || key.isBlank()) {
            return;
        }
        // VİOP teminat-uyarı eşiği adanmış tabloya yönlenir (CHECK constraint + Integer kolon).
        if (KEY_MARGIN_ALERT_THRESHOLD_PCT.equals(key)) {
            int parsed = value == null || value.isNull() ? 50 : value.asInt(50);
            setMarginAlertThresholdPct(userId, parsed);
            return;
        }
        UserPreference pref = repository.findByUserIdAndPrefKey(userId, key)
                .orElseGet(() -> {
                    UserPreference p = new UserPreference();
                    p.setUserId(userId);
                    p.setPrefKey(key);
                    return p;
                });
        pref.setValue(value == null || value.isNull() ? null : value.toString());
        repository.save(pref);
    }

    @Transactional
    public void delete(String userId, String key) {
        repository.findByUserIdAndPrefKey(userId, key).ifPresent(repository::delete);
    }
}
