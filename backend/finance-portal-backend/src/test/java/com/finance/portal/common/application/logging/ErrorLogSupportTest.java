package com.finance.portal.common.application.logging;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorLogSupportTest {

    @Test
    void resolveLevel_warnFor4xx() {
        assertEquals("WARN", ErrorLogSupport.resolveLevel(400));
        assertEquals("WARN", ErrorLogSupport.resolveLevel(HttpStatus.FORBIDDEN));
        assertEquals("WARN", ErrorLogSupport.resolveLevel(404));
    }

    @Test
    void resolveLevel_errorFor5xx() {
        assertEquals("ERROR", ErrorLogSupport.resolveLevel(500));
        assertEquals("ERROR", ErrorLogSupport.resolveLevel(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void categoryIsException() {
        assertEquals("EXCEPTION", ErrorLogSupport.CATEGORY);
    }

    @Test
    void formatResourceNotFoundMessage() {
        assertEquals(
                "Resource not found: /api/v1/does-not-exist-endpoint-xyz",
                ErrorLogSupport.formatResourceNotFoundMessage("/api/v1/does-not-exist-endpoint-xyz"));
    }

    @Test
    void summarizeValidationErrors_listsFirstThreeFields() {
        BindingResult result = bindingResultWithFields(
                field("email", "Geçerli bir e-posta adresi giriniz"),
                field("password", "Şifre boş olamaz"),
                field("username", "Kullanıcı adı boş olamaz"),
                field("firstName", "Ad boş olamaz"),
                field("lastName", "Soyad boş olamaz"));

        String summary = ErrorLogSupport.summarizeValidationErrors(result);

        assertEquals(
                "Validation failed: email Geçerli bir e-posta adresi giriniz, password Şifre boş olamaz, username Kullanıcı adı boş olamaz ... and 2 more",
                summary);
    }

    @Test
    void summarizeValidationErrors_singleField() {
        BindingResult result = bindingResultWithFields(field("email", "must be a well-formed email address"));

        assertEquals(
                "Validation failed: email must be a well-formed email address",
                ErrorLogSupport.summarizeValidationErrors(result));
    }

    @Test
    void formatValidationExceptionSummary_isCompact() {
        BindingResult result = bindingResultWithFields(
                field("email", "must be a well-formed email address"),
                field("password", "must not be blank"));

        String summary = ErrorLogSupport.formatValidationExceptionSummary(result);

        assertTrue(summary.startsWith("org.springframework.web.bind.MethodArgumentNotValidException:"));
        assertTrue(summary.contains("email must be a well-formed email address"));
        assertTrue(summary.length() < 200);
    }

    private static BindingResult bindingResultWithFields(FieldSpec... fields) {
        MapBindingResult result = new MapBindingResult(new HashMap<>(), "registerRequest");
        for (FieldSpec field : fields) {
            result.addError(new FieldError("registerRequest", field.name(), field.message));
        }
        return result;
    }

    private static FieldSpec field(String name, String message) {
        return new FieldSpec(name, message);
    }

    private record FieldSpec(String name, String message) {}
}
