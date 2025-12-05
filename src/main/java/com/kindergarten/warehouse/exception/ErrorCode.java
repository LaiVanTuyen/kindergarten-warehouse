package com.kindergarten.warehouse.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "error.internal", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "validation.failed", HttpStatus.BAD_REQUEST),
    USER_EXISTED(1002, "auth.username.taken", HttpStatus.CONFLICT),
    USER_NOT_FOUND(1003, "error.user.not_found", HttpStatus.NOT_FOUND),
    EMAIL_EXISTED(1004, "auth.email.taken", HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND(1005, "error.category.not_found", HttpStatus.NOT_FOUND),
    TOPIC_NOT_FOUND(1006, "error.topic.not_found", HttpStatus.NOT_FOUND),
    BANNER_NOT_FOUND(1007, "error.banner.not_found", HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(1008, "error.resource.not_found", HttpStatus.NOT_FOUND),
    UNAUTHORIZED(1009, "error.unauthorized", HttpStatus.UNAUTHORIZED),
    FIREBASE_INIT_ERROR(1010, "error.firebase.init", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(1011, "auth.login_failed", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1012, "error.forbidden", HttpStatus.FORBIDDEN),
    ;

    private int code;
    private String message;
    private HttpStatus httpStatusCode;

    ErrorCode(int code, String message, HttpStatus httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }
}
