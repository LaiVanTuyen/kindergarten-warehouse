package com.kindergarten.warehouse.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // ========== General Errors (9xxx) ==========
    UNCATEGORIZED_EXCEPTION(9999, "error.internal", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(9001, "error.invalid.request", HttpStatus.BAD_REQUEST),

    // ========== Validation Errors (1xxx) ==========
    INVALID_KEY(1001, "validation.failed", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(1002, "validation.failed", HttpStatus.BAD_REQUEST),

    // ========== Authentication & Authorization (10xx) ==========
    UNAUTHENTICATED(1011, "auth.login_failed", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1009, "error.unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1012, "error.forbidden", HttpStatus.FORBIDDEN),
    INVALID_PASSWORD(1013, "auth.password.invalid", HttpStatus.BAD_REQUEST),

    // ========== User Errors (20xx) ==========
    USER_EXISTED(2001, "auth.username.taken", HttpStatus.CONFLICT),
    USER_NOT_FOUND(2003, "error.user.not_found", HttpStatus.NOT_FOUND),
    EMAIL_EXISTED(2004, "auth.email.taken", HttpStatus.CONFLICT),

    // ========== Category Errors (30xx) ==========
    CATEGORY_NOT_FOUND(3001, "error.category.not_found", HttpStatus.NOT_FOUND),

    // ========== Topic Errors (40xx) ==========
    TOPIC_NOT_FOUND(4001, "error.topic.not_found", HttpStatus.NOT_FOUND),

    // ========== Banner Errors (50xx) ==========
    BANNER_NOT_FOUND(5001, "error.banner.not_found", HttpStatus.NOT_FOUND),

    // ========== Resource Errors (60xx) ==========
    RESOURCE_NOT_FOUND(6001, "error.resource.not_found", HttpStatus.NOT_FOUND),
    FILE_UPLOAD_ERROR(6002, "error.file.upload", HttpStatus.BAD_REQUEST),

    // ========== Duplicate Errors (70xx) ==========
    DUPLICATE_SLUG(7001, "error.duplicate.slug", HttpStatus.CONFLICT),
    DUPLICATE_NAME(7002, "error.duplicate.name", HttpStatus.CONFLICT),
    DUPLICATE_ENTRY(7003, "error.duplicate.entry", HttpStatus.CONFLICT),

    // ========== System Errors (80xx) ==========
    FIREBASE_INIT_ERROR(8001, "error.firebase.init", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    private final int code;
    private final String message;
    private final HttpStatus httpStatusCode;

    ErrorCode(int code, String message, HttpStatus httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }
}
