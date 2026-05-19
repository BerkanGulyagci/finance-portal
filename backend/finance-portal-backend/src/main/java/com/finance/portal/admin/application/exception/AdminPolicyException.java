package com.finance.portal.admin.application.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AdminPolicyException extends RuntimeException {

    private final HttpStatus status;

    public AdminPolicyException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
