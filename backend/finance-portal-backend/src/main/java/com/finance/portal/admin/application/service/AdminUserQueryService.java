package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final AdminUserEnrichmentService adminUserEnrichmentService;
    private final TemporaryBanExpiryService temporaryBanExpiryService;

    public List<AdminUserView> listUsers(String search, int first, int max) {
        validatePagination(first, max);
        List<AdminUserView> keycloakUsers = keycloakUserAdminPort.listUsers(normalizeSearch(search), first, max);
        List<AdminUserView> enriched = adminUserEnrichmentService.enrichUsers(keycloakUsers);
        return temporaryBanExpiryService.expireIfNeeded(enriched);
    }

    public AdminUserView getUser(String userId) {
        AdminUserView keycloakUser = keycloakUserAdminPort.getUser(userId);
        AdminUserView enriched = adminUserEnrichmentService.enrichUser(keycloakUser);
        return temporaryBanExpiryService.expireIfNeeded(enriched);
    }

    private static void validatePagination(int first, int max) {
        if (first < 0) {
            throw new IllegalArgumentException("'first' 0 veya daha büyük olmalıdır.");
        }
        if (max < 1 || max > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("'max' 1 ile " + MAX_PAGE_SIZE + " arasında olmalıdır.");
        }
    }

    private static String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
