package com.finance.portal.admin.infrastructure.keycloak;

import com.finance.portal.admin.infrastructure.keycloak.dto.KeycloakUserRepresentation;
import com.finance.portal.common.application.port.UserAccountStatusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class KeycloakUserAccountStatusAdapter implements UserAccountStatusPort {

    private static final long CACHE_TTL_MS = 15_000;

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminRestClient restClient;
    private final ConcurrentHashMap<String, CachedStatus> cache = new ConcurrentHashMap<>();

    @Override
    public boolean isAccountEnabled(String userId) {
        long now = System.currentTimeMillis();
        CachedStatus cached = cache.get(userId);
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.enabled();
        }

        KeycloakUserRepresentation user = restClient.get(userUrl(userId), KeycloakUserRepresentation.class);
        boolean enabled = Boolean.TRUE.equals(user.getEnabled());
        cache.put(userId, new CachedStatus(enabled, now + CACHE_TTL_MS));
        return enabled;
    }

    @Override
    public void evictAccountStatus(String userId) {
        cache.remove(userId);
    }

    private String userUrl(String userId) {
        return properties.adminApiBase() + "/users/" + userId;
    }

    private record CachedStatus(boolean enabled, long expiresAtMs) {
    }
}
