package com.finance.portal.admin.application.service;

import com.finance.portal.admin.application.model.AdminUserListResult;
import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.application.model.BanStatus;
import com.finance.portal.admin.application.port.KeycloakUserAdminPort;
import com.finance.portal.admin.presentation.dto.AdminBanStatusFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int KEYCLOAK_SCAN_BATCH = 50;
    private static final int KEYCLOAK_SCAN_LIMIT = 500;

    private final KeycloakUserAdminPort keycloakUserAdminPort;
    private final AdminUserEnrichmentService adminUserEnrichmentService;
    private final TemporaryBanExpiryService temporaryBanExpiryService;

    public AdminUserListResult listUsers(String search, int first, int max, AdminBanStatusFilter statusFilter) {
        validatePagination(first, max);
        AdminBanStatusFilter filter = statusFilter != null ? statusFilter : AdminBanStatusFilter.ALL;

        if (filter == AdminBanStatusFilter.ALL) {
            return listUsersWithoutStatusFilter(normalizeSearch(search), first, max);
        }
        return listUsersWithStatusFilter(normalizeSearch(search), first, max, filter);
    }

    private AdminUserListResult listUsersWithoutStatusFilter(String search, int first, int max) {
        List<AdminUserView> keycloakUsers = keycloakUserAdminPort.listUsers(search, first, max + 1);
        boolean hasMore = keycloakUsers.size() > max;
        List<AdminUserView> page = hasMore ? new ArrayList<>(keycloakUsers.subList(0, max)) : keycloakUsers;
        List<AdminUserView> enriched = adminUserEnrichmentService.enrichUsers(page);
        List<AdminUserView> users = temporaryBanExpiryService.expireIfNeeded(enriched);
        return new AdminUserListResult(users, hasMore);
    }

    private AdminUserListResult listUsersWithStatusFilter(String search, int first, int max, AdminBanStatusFilter filter) {
        List<AdminUserView> matched = new ArrayList<>();
        int skipped = 0;
        int kcOffset = 0;

        while (matched.size() < max && kcOffset < KEYCLOAK_SCAN_LIMIT) {
            List<AdminUserView> batch = listUsersWithoutStatusFilter(search, kcOffset, KEYCLOAK_SCAN_BATCH).getUsers();
            if (batch.isEmpty()) {
                break;
            }

            for (AdminUserView user : batch) {
                if (!matchesStatusFilter(user, filter)) {
                    continue;
                }
                if (skipped < first) {
                    skipped++;
                    continue;
                }
                matched.add(user);
                if (matched.size() >= max) {
                    break;
                }
            }

            if (batch.size() < KEYCLOAK_SCAN_BATCH) {
                break;
            }
            kcOffset += KEYCLOAK_SCAN_BATCH;
        }

        boolean hasMore = matched.size() >= max && kcOffset < KEYCLOAK_SCAN_LIMIT;
        return new AdminUserListResult(matched, hasMore);
    }

    private static boolean matchesStatusFilter(AdminUserView user, AdminBanStatusFilter filter) {
        boolean active = user.getBanStatus() == BanStatus.ACTIVE;
        return switch (filter) {
            case ACTIVE -> active;
            case BANNED -> !active;
            case ALL -> true;
        };
    }

    public AdminUserView getUser(String userId) {
        AdminUserView keycloakUser = keycloakUserAdminPort.getUser(userId);
        AdminUserView enriched = adminUserEnrichmentService.enrichUser(keycloakUser);
        return temporaryBanExpiryService.expireIfNeeded(enriched);
    }

    /** Belirli id'lerdeki kullanıcıları getirir (admin "talep açanlar" filtresi). Bulunamayanlar atlanır. */
    public AdminUserListResult listUsersByIds(List<String> userIds) {
        List<AdminUserView> users = new ArrayList<>();
        for (String id : userIds) {
            try {
                users.add(getUser(id));
            } catch (Exception ignored) {
                // Keycloak'ta bulunamayan / silinmiş kullanıcıyı atla
            }
        }
        return new AdminUserListResult(users, false);
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
