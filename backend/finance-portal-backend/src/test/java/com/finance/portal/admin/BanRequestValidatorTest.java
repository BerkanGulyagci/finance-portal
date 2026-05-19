package com.finance.portal.admin;

import com.finance.portal.admin.application.service.BanRequestValidator;
import com.finance.portal.admin.presentation.dto.BanType;
import com.finance.portal.admin.presentation.dto.BanUserRequest;
import com.finance.portal.admin.presentation.dto.DurationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BanRequestValidatorTest {

    @Test
    void shouldRequireBanType() {
        assertThrows(IllegalArgumentException.class, () -> BanRequestValidator.validate(null));
        assertThrows(IllegalArgumentException.class, () -> BanRequestValidator.validate(new BanUserRequest()));
    }

    @Test
    void shouldAcceptPermanentBanWithoutDuration() {
        BanUserRequest request = new BanUserRequest();
        request.setBanType(BanType.PERMANENT);
        assertDoesNotThrow(() -> BanRequestValidator.validate(request));
    }

    @Test
    void shouldRejectDurationFieldsOnPermanentBan() {
        BanUserRequest request = new BanUserRequest();
        request.setBanType(BanType.PERMANENT);
        request.setDurationValue(1);
        request.setDurationUnit(DurationUnit.HOURS);
        assertThrows(IllegalArgumentException.class, () -> BanRequestValidator.validate(request));
    }

    @Test
    void shouldRequireDurationForTemporaryBan() {
        BanUserRequest request = new BanUserRequest();
        request.setBanType(BanType.TEMPORARY);
        assertThrows(IllegalArgumentException.class, () -> BanRequestValidator.validate(request));
    }

    @Test
    void shouldRejectNonPositiveDurationValue() {
        BanUserRequest request = new BanUserRequest();
        request.setBanType(BanType.TEMPORARY);
        request.setDurationValue(0);
        request.setDurationUnit(DurationUnit.MINUTES);
        assertThrows(IllegalArgumentException.class, () -> BanRequestValidator.validate(request));
    }

    @Test
    void shouldAcceptTemporaryBanWithValidDuration() {
        BanUserRequest request = new BanUserRequest();
        request.setBanType(BanType.TEMPORARY);
        request.setDurationValue(365);
        request.setDurationUnit(DurationUnit.DAYS);
        assertDoesNotThrow(() -> BanRequestValidator.validate(request));
    }
}
