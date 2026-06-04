package com.finance.portal.admin.presentation.controller;

import com.finance.portal.admin.application.service.AdminUserQueryService;
import com.finance.portal.admin.application.service.BanDurationCalculator;
import com.finance.portal.admin.application.service.BanUserService;
import com.finance.portal.admin.application.service.UnbanUserService;
import com.finance.portal.admin.presentation.dto.AdminBanStatusFilter;
import com.finance.portal.admin.presentation.dto.AdminUserListResponse;
import com.finance.portal.admin.presentation.dto.AdminUserResponse;
import com.finance.portal.admin.presentation.dto.BanType;
import com.finance.portal.admin.presentation.dto.BanUserRequest;
import com.finance.portal.admin.presentation.mapper.AdminPresentationMapper;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.support.application.service.SupportTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserQueryService adminUserQueryService;
    private final BanUserService banUserService;
    private final UnbanUserService unbanUserService;
    private final AdminPresentationMapper mapper;
    private final SupportTicketService supportTicketService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserListResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int first,
            @RequestParam(defaultValue = "20") int max,
            @RequestParam(defaultValue = "ALL") AdminBanStatusFilter status,
            @RequestParam(defaultValue = "false") boolean withTickets
    ) {
        AdminUserListResponse data = withTickets
                ? mapper.toListResponse(
                        adminUserQueryService.listUsersByIds(supportTicketService.userIdsWithActiveTickets()), first, max)
                : mapper.toListResponse(adminUserQueryService.listUsers(search, first, max, status), first, max);

        // Her kullanıcıya aktif destek talebi sayısını ekle (liste rozeti için).
        java.util.Map<String, Long> counts = supportTicketService.activeTicketCountByUser();
        if (data.getUsers() != null) {
            data.getUsers().forEach(u -> u.setActiveTicketCount(counts.getOrDefault(u.getId(), 0L)));
        }
        return ResponseEntity.ok(ApiResponse.success(data, "Kullanıcılar listelendi."));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable String userId) {
        AdminUserResponse data = mapper.toResponse(adminUserQueryService.getUser(userId));
        data.setActiveTicketCount(supportTicketService.activeTicketCountByUser().getOrDefault(userId, 0L));
        return ResponseEntity.ok(ApiResponse.success(data, "Kullanıcı bilgisi getirildi."));
    }

    @PostMapping("/{userId}/ban")
    public ResponseEntity<ApiResponse<Void>> banUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody BanUserRequest request
    ) {
        banUserService.banUser(userId, jwt.getSubject(), request);
        String message = request.getBanType() == BanType.PERMANENT
                ? "Kullanıcı kalıcı olarak banlandı."
                : "Kullanıcı "
                + BanDurationCalculator.formatDuration(request.getDurationValue(), request.getDurationUnit())
                + " süreyle banlandı.";
        return ResponseEntity.ok(ApiResponse.success(null, message));
    }

    @PostMapping("/{userId}/unban")
    public ResponseEntity<ApiResponse<Void>> unbanUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId
    ) {
        unbanUserService.unbanUser(userId, jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(null, "Kullanıcının banı kaldırıldı."));
    }
}
