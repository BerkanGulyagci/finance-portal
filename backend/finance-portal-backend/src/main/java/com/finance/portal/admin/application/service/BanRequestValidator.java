package com.finance.portal.admin.application.service;

import com.finance.portal.admin.presentation.dto.BanType;
import com.finance.portal.admin.presentation.dto.BanUserRequest;
public final class BanRequestValidator {

    private BanRequestValidator() {
    }

    public static void validate(BanUserRequest request) {
        if (request == null || request.getBanType() == null) {
            throw new IllegalArgumentException("banType zorunludur.");
        }

        if (request.getBanType() == BanType.PERMANENT) {
            if (request.getDurationValue() != null || request.getDurationUnit() != null) {
                throw new IllegalArgumentException("Kalıcı ban için durationValue ve durationUnit gönderilmemelidir.");
            }
            return;
        }

        if (request.getBanType() != BanType.TEMPORARY) {
            throw new IllegalArgumentException("banType yalnızca PERMANENT veya TEMPORARY olabilir.");
        }

        if (request.getDurationValue() == null) {
            throw new IllegalArgumentException("Geçici ban için durationValue zorunludur.");
        }
        if (request.getDurationValue() <= 0) {
            throw new IllegalArgumentException("durationValue pozitif tam sayı olmalıdır.");
        }
        if (request.getDurationUnit() == null) {
            throw new IllegalArgumentException("Geçici ban için durationUnit zorunludur.");
        }
    }
}
