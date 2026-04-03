package com.kindergarten.warehouse.exception;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.rollbar.notifier.Rollbar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final com.kindergarten.warehouse.service.MessageService messageService;
    private final Rollbar rollbar;

    /**
     * Handle validation errors from @Valid annotation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        return new ResponseEntity<>(
                ApiResponse.<Map<String, String>>builder()
                        .code(HttpStatus.BAD_REQUEST.value())
                        .message(messageService.getMessage("validation.failed"))
                        .result(errors)
                        .timestamp(java.time.LocalDateTime.now())
                        .build(),
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle constraint violations from @Validated annotation
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage));

        log.warn("Constraint violation: {}", errors);

        return new ResponseEntity<>(
                ApiResponse.<Map<String, String>>builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(messageService.getMessage(ErrorCode.VALIDATION_ERROR.getMessage()))
                        .result(errors)
                        .timestamp(java.time.LocalDateTime.now())
                        .build(),
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle malformed JSON requests
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }

    /**
     * Handle application-specific exceptions
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();

        log.warn("AppException: {} - {}", errorCode.name(), errorCode.getMessage());

        String message = ex.getParams() != null && ex.getParams().length > 0
                ? messageService.getMessage(errorCode.getMessage(), ex.getParams())
                : messageService.getMessage(errorCode.getMessage());

        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), message),
                errorCode.getHttpStatusCode());
    }

    /**
     * Handle authentication failures
     */
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            org.springframework.security.authentication.BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());

        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }

    /**
     * Handle authorization failures
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }

    /**
     * Handle database constraint violations (duplicate keys, etc.)
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
            org.springframework.dao.DataIntegrityViolationException ex) {
        ErrorCode errorCode = ErrorCode.DUPLICATE_ENTRY;

        String exceptionMessage = ex.getMessage();
        if (exceptionMessage != null) {
            if (exceptionMessage.contains("slug") || exceptionMessage.contains("SLUG")) {
                errorCode = ErrorCode.DUPLICATE_SLUG;
            } else if (exceptionMessage.contains("name") || exceptionMessage.contains("NAME")) {
                errorCode = ErrorCode.DUPLICATE_NAME;
            }
        }

        log.warn("Data integrity violation: {} - {}", errorCode.name(), ex.getMessage());

        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }

    /**
     * Handle all uncaught exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex) {
        log.error("Uncategorized exception", ex);
        rollbar.error(ex);

        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }
}
