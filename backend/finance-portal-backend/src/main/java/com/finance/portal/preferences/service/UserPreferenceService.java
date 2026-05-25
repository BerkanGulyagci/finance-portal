package com.finance.portal.preferences.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.preferences.domain.UserPreference;
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
 */
@Service
public class UserPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceService.class);

    private final UserPreferenceRepository repository;
    private final ObjectMapper objectMapper;

    public UserPreferenceService(UserPreferenceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
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
        return out;
    }

    @Transactional
    public void upsert(String userId, String key, JsonNode value) {
        if (key == null || key.isBlank()) {
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
