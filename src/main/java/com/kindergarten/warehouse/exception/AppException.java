package com.kindergarten.warehouse.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] params;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.params = null;
    }

    public AppException(ErrorCode errorCode, Object... params) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.params = params;
    }

    public AppException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.params = null;
    }

    public AppException(ErrorCode errorCode, Throwable cause, Object... params) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.params = params;
    }
}
