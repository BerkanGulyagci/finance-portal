package com.finance.portal.alarm.presentation.controller;

import com.finance.portal.alarm.application.service.AlarmService;
import com.finance.portal.alarm.presentation.dto.AlarmResponse;
import com.finance.portal.alarm.presentation.dto.CreateAlarmRequest;
import com.finance.portal.alarm.presentation.dto.UpdateAlarmRequest;
import com.finance.portal.alarm.presentation.mapper.AlarmMapper;
import com.finance.portal.common.presentation.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AlarmResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAlarmRequest request
    ) {
        String userId = jwt.getSubject();
        String recipientEmail = verifiedEmail(jwt);
        AlarmResponse body = AlarmMapper.toResponse(alarmService.create(userId, recipientEmail, request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(body, "Alarm created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AlarmResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<AlarmResponse> body = alarmService.list(userId).stream()
                .map(AlarmMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body, "Alarms retrieved successfully"));
    }

    @GetMapping("/{alarmId}")
    public ResponseEntity<ApiResponse<AlarmResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID alarmId
    ) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(ApiResponse.success(
                AlarmMapper.toResponse(alarmService.get(userId, alarmId)), "Alarm retrieved successfully"));
    }

    @PatchMapping("/{alarmId}")
    public ResponseEntity<ApiResponse<AlarmResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID alarmId,
            @RequestBody UpdateAlarmRequest request
    ) {
        String userId = jwt.getSubject();
        return ResponseEntity.ok(ApiResponse.success(
                AlarmMapper.toResponse(alarmService.update(userId, alarmId, request)), "Alarm updated successfully"));
    }

    @DeleteMapping("/{alarmId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID alarmId
    ) {
        String userId = jwt.getSubject();
        alarmService.delete(userId, alarmId);
        return ResponseEntity.ok(ApiResponse.success(null, "Alarm deleted successfully"));
    }

    /** Yalnızca doğrulanmış e-postaya bildirim gönderilir; doğrulanmamışsa null (e-posta atlanır). */
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
}
