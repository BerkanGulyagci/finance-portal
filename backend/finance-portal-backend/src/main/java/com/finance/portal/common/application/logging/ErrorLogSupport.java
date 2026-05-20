package com.finance.portal.common.application.logging;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;

import java.util.stream.Collectors;

public final class ErrorLogSupport {

    public static final String CATEGORY = "ERROR";
    public static final String SERVICE_NAME = "finance-portal-backend";

    public static final String EVENT_GLOBAL_EXCEPTION_HANDLED = "GLOBAL_EXCEPTION_HANDLED";
    public static final String EVENT_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String EVENT_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String EVENT_ACCESS_DENIED = "ACCESS_DENIED";
    public static final String EVENT_UNAUTHORIZED_ACCESS = "UNAUTHORIZED_ACCESS";

    private static final int MAX_VALIDATION_ERRORS_SHOWN = 3;

    private ErrorLogSupport() {}

    public static String formatResourceNotFoundMessage(String path) {
        return "Resource not found: " + path;
    }

    public static String formatResourceNotFoundExceptionSummary(String path) {
        return "Resource not found: " + path;
    }

    public static String summarizeValidationErrors(org.springframework.validation.BindingResult bindingResult) {
        var errors = bindingResult.getAllErrors();
        if (errors.isEmpty()) {
            return "Validation failed";
        }

        int limit = Math.min(MAX_VALIDATION_ERRORS_SHOWN, errors.size());
        String joined = errors.stream()
                .limit(limit)
                .map(error -> {
                    if (error instanceof FieldError fieldError) {
                        String msg = fieldError.getDefaultMessage();
                        return fieldError.getField() + (msg != null ? " " + msg : "");
                    }
                    return error.getDefaultMessage() != null ? error.getDefaultMessage() : "invalid";
                })
                .collect(Collectors.joining(", "));

        int remaining = errors.size() - limit;
        if (remaining > 0) {
            joined += " ... and " + remaining + " more";
        }
        return "Validation failed: " + joined;
    }

    public static String formatValidationExceptionSummary(org.springframework.validation.BindingResult bindingResult) {
        return org.springframework.web.bind.MethodArgumentNotValidException.class.getName()
                + ": " + summarizeValidationErrors(bindingResult);
    }

    public static String resolveLevel(int httpStatus) {
        if (httpStatus >= 500) {
            return "ERROR";
        }
        return "WARN";
    }

    public static String resolveLevel(HttpStatus status) {
        return resolveLevel(status.value());
    }
}
