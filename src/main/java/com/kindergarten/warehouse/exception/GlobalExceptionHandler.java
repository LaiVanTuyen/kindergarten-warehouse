package com.kindergarten.warehouse.exception;

import com.kindergarten.warehouse.dto.response.ApiResponse;
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

    private final com.kindergarten.warehouse.service.MessageService messageService;

    public GlobalExceptionHandler(com.kindergarten.warehouse.service.MessageService messageService) {
        this.messageService = messageService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            // Try to localize if it looks like a key (simple heuristic or just trust DTOs
            // are updated)
            // Since we updated DTOs to use keys, we should localize here if possible.
            // But wait, the DTOs have keys like "{validation.required}". Spring Validation
            // resolves these automatically if MessageSource is configured correctly.
            // However, if we put keys directly in `message="..."` without `{}` it might not
            // resolve.
            // In our previous step, we used `{validation.required}`. Spring resolves this
            // BEFORE it gets here if using standard validation.
            // But if it returns the resolved message, we are good.
            // If it returns the key, we might need to resolve it.
            // Let's assume Spring resolves it because we used `{}`.
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(
                ApiResponse.error(HttpStatus.BAD_REQUEST.value(), messageService.getMessage("validation.failed")),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            org.springframework.security.authentication.BadCredentialsException ex) {
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException ex) {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex) {
        ErrorCode errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        return new ResponseEntity<>(
                ApiResponse.error(errorCode.getCode(), messageService.getMessage(errorCode.getMessage())),
                errorCode.getHttpStatusCode());
    }
}
