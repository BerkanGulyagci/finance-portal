package com.finance.portal.common.presentation.exception;

import com.finance.portal.common.infrastructure.exception.ExternalApiException;
import com.finance.portal.common.infrastructure.exception.ResourceNotFoundException;
import com.finance.portal.common.presentation.dto.ApiResponse;
import com.finance.portal.market.application.viop.UnsupportedViopContractException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    /**
     * Validation hataları — kullanıcı girdisi yanlış, WARN yeterli.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        logger.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return new ResponseEntity<>(ApiResponse.error("Validation failed", errors), HttpStatus.BAD_REQUEST);
    }

    /**
     * Desteklenmeyen VİOP sözleşmesi — grafik gösterilemiyor, 422 Unprocessable.
     */
    @ExceptionHandler(UnsupportedViopContractException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnsupportedViopContract(UnsupportedViopContractException ex) {
        logger.warn("Unsupported VIOP contract for chart: {}", ex.getMessage());
        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Geçersiz argüman — istemci hatası, WARN yeterli.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        logger.warn("Illegal argument: {}", ex.getMessage());

        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    /**
     * Kaynak bulunamadı — 404, WARN seviyesi.
     * INFO değil WARN: bulunamayan kaynak bir uyarı sinyalidir.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {

        logger.warn("Resource not found: {}", ex.getMessage());

        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    /**
     * Dış API hatası — servis bağımlılığı başarısız, ERROR + stack trace.
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleExternalApiException(ExternalApiException ex) {

        logger.error("External API error: {}", ex.getMessage(), ex);

        return new ResponseEntity<>(ApiResponse.failure(ex.getMessage()), HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Beklenmeyen hata — her zaman ERROR + full stack trace.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {

        logger.error("Unexpected error: {}", ex.getMessage(), ex);

        return new ResponseEntity<>(
                ApiResponse.error("An unexpected error occurred. Please try again later."),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
