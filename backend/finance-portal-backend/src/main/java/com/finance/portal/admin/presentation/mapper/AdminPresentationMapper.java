package com.finance.portal.admin.presentation.mapper;

import com.finance.portal.admin.application.model.AdminUserView;
import com.finance.portal.admin.presentation.dto.AdminUserListResponse;
import com.finance.portal.admin.presentation.dto.AdminUserResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdminPresentationMapper {

    public AdminUserResponse toResponse(AdminUserView view) {
        AdminUserResponse response = new AdminUserResponse();
        response.setId(view.getId());
        response.setUsername(view.getUsername());
        response.setEmail(view.getEmail());
        response.setFirstName(view.getFirstName());
        response.setLastName(view.getLastName());
        response.setEnabled(view.isEnabled());
        response.setRoles(view.getRoles());
        response.setBanUntil(view.getBanUntil());
        response.setPermanentBan(view.isPermanentBan());
        response.setBanStatus(view.getBanStatus());
        return response;
    }

    public AdminUserListResponse toListResponse(List<AdminUserView> users, int first, int max) {
        AdminUserListResponse response = new AdminUserListResponse();
        response.setUsers(users.stream().map(this::toResponse).toList());
        response.setFirst(first);
        response.setMax(max);
        return response;
    }
}
