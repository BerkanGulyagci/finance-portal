package com.finance.portal.common.presentation.exception;



import com.finance.portal.admin.application.exception.AdminPolicyException;

import com.finance.portal.common.application.exception.ExternalApiException;

import com.finance.portal.common.application.exception.ResourceNotFoundException;

import com.finance.portal.common.application.logging.CentralErrorLogService;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;

import com.finance.portal.common.application.logging.ErrorLogSupport;

import com.finance.portal.common.application.logging.IntegrationLogSupport;

import com.finance.portal.common.presentation.dto.ApiResponse;

import com.finance.portal.market.application.viop.UnsupportedViopContractException;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;

import org.apache.logging.log4j.Logger;

import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;

import org.springframework.validation.FieldError;

import org.springframework.web.bind.MethodArgumentNotValidException;

import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.servlet.NoHandlerFoundException;

import org.springframework.web.servlet.resource.NoResourceFoundException;



import java.util.HashMap;

import java.util.Map;



@RestControllerAdvice

public class GlobalExceptionHandler {



    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);



    private final CentralErrorLogService centralErrorLogService;

    private final CentralIntegrationLogService centralIntegrationLogService;



    public GlobalExceptionHandler(CentralErrorLogService centralErrorLogService,
                                  CentralIntegrationLogService centralIntegrationLogService) {

        this.centralErrorLogService = centralErrorLogService;
        this.centralIntegrationLogService = centralIntegrationLogService;

    }



    @ExceptionHandler(MethodArgumentNotValidException.class)

    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(

            MethodArgumentNotValidException ex) {



        String summary = ErrorLogSupport.summarizeValidationErrors(ex.getBindingResult());

        centralErrorLogService.logHandledError(

                HttpStatus.BAD_REQUEST,

                ErrorLogSupport.EVENT_VALIDATION_FAILED,

                summary,

                ErrorLogSupport.formatValidationExceptionSummary(ex.getBindingResult()),

                GlobalExceptionHandler.class.getName());



        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {

            String fieldName = ((FieldError) error).getField();

            String errorMessage = error.getDefaultMessage();

            errors.put(fieldName, errorMessage);

        });



        return new ResponseEntity<>(ApiResponse.error("Validation failed", errors), HttpStatus.BAD_REQUEST);

    }



    @ExceptionHandler(NoResourceFoundException.class)

    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(

            NoResourceFoundException ex, HttpServletRequest request) {

        return handleUnknownRoute(request.getRequestURI(), ex);

    }



    @ExceptionHandler(NoHandlerFoundException.class)

    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFound(

            NoHandlerFoundException ex, HttpServletRequest request) {

        return handleUnknownRoute(request.getRequestURI(), ex);

    }



    @ExceptionHandler(ResponseStatusException.class)

    public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(

            ResponseStatusException ex, HttpServletRequest request) {

        if (ex.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {

            centralErrorLogService.logHandledError(

                    HttpStatus.valueOf(ex.getStatusCode().value()),

                    ErrorLogSupport.EVENT_GLOBAL_EXCEPTION_HANDLED,

                    ex.getReason() != null ? ex.getReason() : ex.getMessage(),

                    ex);

            return new ResponseEntity<>(

                    ApiResponse.error(ex.getReason() != null ? ex.getReason() : "Request failed"),

                    ex.getStatusCode());

        }

        return handleUnknownRoute(request.getRequestURI(), ex);

    }



    @ExceptionHandler(UnsupportedViopContractException.class)

    public ResponseEntity<ApiResponse<Object>> handleUnsupportedViopContract(UnsupportedViopContractException ex) {

        centralErrorLogService.logHandledError(

                HttpStatus.UNPROCESSABLE_ENTITY,

                ErrorLogSupport.EVENT_VALIDATION_FAILED,

                "Unsupported VIOP contract for chart: " + ex.getMessage(),

                ex);



        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.UNPROCESSABLE_ENTITY);

    }



    @ExceptionHandler(IllegalArgumentException.class)

    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(

            IllegalArgumentException ex) {



        centralErrorLogService.logHandledError(

                HttpStatus.BAD_REQUEST,

                ErrorLogSupport.EVENT_VALIDATION_FAILED,

                "Illegal argument: " + ex.getMessage(),

                ex);



        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.BAD_REQUEST);

    }



    @ExceptionHandler(AdminPolicyException.class)

    public ResponseEntity<ApiResponse<Object>> handleAdminPolicyException(AdminPolicyException ex) {

        centralErrorLogService.logHandledError(

                ex.getStatus(),

                ErrorLogSupport.EVENT_ACCESS_DENIED,

                "Admin policy violation: " + ex.getMessage(),

                ex);



        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), ex.getStatus());

    }



    /**

     * Domain 404 — REQUEST log yeterli; merkezi ERROR event gönderilmez.

     */

    @ExceptionHandler(ResourceNotFoundException.class)

    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(

            ResourceNotFoundException ex) {



        logger.warn("Resource not found: {}", ex.getMessage());



        return new ResponseEntity<>(ApiResponse.error(ex.getMessage()), HttpStatus.NOT_FOUND);

    }



    /**

     * Dış API hatası — INTEGRATION pipeline; ERROR category duplicate üretilmez.

     */

    @ExceptionHandler(ExternalApiException.class)

    public ResponseEntity<ApiResponse<Object>> handleExternalApiException(ExternalApiException ex) {



        logger.error("External API error: {}", ex.getMessage(), ex);

        String provider = CentralIntegrationLogService.inferProviderFromMessage(ex.getMessage());
        centralIntegrationLogService.publish(
                IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                "ERROR",
                "External API call failed",
                provider,
                "api_call",
                "503",
                null,
                null,
                null,
                Map.of("trigger", IntegrationLogSupport.TRIGGER_API_REQUEST),
                GlobalExceptionHandler.class.getName()
        );



        return new ResponseEntity<>(ApiResponse.failure(ex.getMessage()), HttpStatus.SERVICE_UNAVAILABLE);

    }



    @ExceptionHandler(Exception.class)

    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {



        centralErrorLogService.logHandledError(

                HttpStatus.INTERNAL_SERVER_ERROR,

                ErrorLogSupport.EVENT_GLOBAL_EXCEPTION_HANDLED,

                "Unexpected error: " + ex.getMessage(),

                ex);



        return new ResponseEntity<>(

                ApiResponse.error("An unexpected error occurred. Please try again later."),

                HttpStatus.INTERNAL_SERVER_ERROR);

    }



    private ResponseEntity<ApiResponse<Object>> handleUnknownRoute(String path, Throwable ex) {

        String message = ErrorLogSupport.formatResourceNotFoundMessage(path);

        centralErrorLogService.logHandledError(

                HttpStatus.NOT_FOUND,

                ErrorLogSupport.EVENT_RESOURCE_NOT_FOUND,

                message,

                ErrorLogSupport.formatResourceNotFoundExceptionSummary(path),

                GlobalExceptionHandler.class.getName());



        return new ResponseEntity<>(ApiResponse.error(message), HttpStatus.NOT_FOUND);

    }

}


