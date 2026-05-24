package com.finance.portal.admin.infrastructure.persistence;

import com.finance.portal.admin.application.model.UserBanState;
import com.finance.portal.admin.application.port.UserBanStatePort;
import com.finance.portal.admin.infrastructure.persistence.entity.UserBanStateEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UserBanStateJpaAdapter implements UserBanStatePort {

    private final UserBanStateRepository repository;

    @Override
    public void save(UserBanState state) {
        UserBanStateEntity entity = repository.findById(state.getKeycloakUserId())
                .orElseGet(UserBanStateEntity::new);
        entity.setKeycloakUserId(state.getKeycloakUserId());
        entity.setPermanent(state.isPermanent());
        entity.setBanUntil(state.getBanUntil());
        entity.setBanReason(state.getBanReason());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(state.getCreatedAt() != null ? state.getCreatedAt() : Instant.now());
        }
        repository.save(entity);
    }

    @Override
    public void deleteByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        repository.deleteById(userId);
    }

    @Override
    public Optional<UserBanState> findByUserId(String userId) {
        return repository.findById(userId).map(this::toModel);
    }

    @Override
    public Map<String, UserBanState> findByUserIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByKeycloakUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserBanStateEntity::getKeycloakUserId, this::toModel));
    }

    @Override
    public List<UserBanState> findExpiredTemporaryBans(Instant now) {
        return repository.findExpiredTemporaryBans(now).stream()
                .map(this::toModel)
                .toList();
    }

    private UserBanState toModel(UserBanStateEntity entity) {
        return new UserBanState(
                entity.getKeycloakUserId(),
                entity.isPermanent(),
                entity.getBanUntil(),
                entity.getCreatedAt(),
                entity.getBanReason()
        );
    }
}
