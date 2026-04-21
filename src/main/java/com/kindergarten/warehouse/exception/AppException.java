package com.kindergarten.warehouse.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] params;
    /** Nếu > 0, {@link GlobalExceptionHandler} sẽ set HTTP header {@code Retry-After}. */
    private final long retryAfterSeconds;

    public AppException(ErrorCode errorCode) {
        this(errorCode, 0L, null, null);
    }

    public AppException(ErrorCode errorCode, Object... params) {
        this(errorCode, 0L, null, params);
    }

    public AppException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, 0L, cause, null);
    }

    public AppException(ErrorCode errorCode, Throwable cause, Object... params) {
        this(errorCode, 0L, cause, params);
    }

    /** Dùng cho rate-limit / throttle errors để FE biết chờ bao lâu. */
    public static AppException withRetryAfter(ErrorCode errorCode, long retryAfterSeconds) {
        return new AppException(errorCode, Math.max(0L, retryAfterSeconds), null, null);
    }

    private AppException(ErrorCode errorCode, long retryAfterSeconds, Throwable cause, Object[] params) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.params = params;
    }
}
