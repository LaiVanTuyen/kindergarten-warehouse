package com.kindergarten.warehouse.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // ========== General Errors (9xxx) ==========
    UNCATEGORIZED_EXCEPTION(9999, "error.internal", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(9001, "error.invalid.request", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(9002, "error.method_not_allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(9003, "error.unsupported_media_type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    ENDPOINT_NOT_FOUND(9004, "error.endpoint.not_found", HttpStatus.NOT_FOUND),

    // ========== Validation Errors (1xxx) ==========
    INVALID_KEY(1001, "validation.failed", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(1002, "validation.failed", HttpStatus.BAD_REQUEST),

    // ========== Authentication & Authorization (10xx) ==========
    UNAUTHENTICATED(1011, "auth.login_failed", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1009, "error.unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1012, "error.forbidden", HttpStatus.FORBIDDEN),
    INVALID_PASSWORD(1013, "auth.password.invalid", HttpStatus.BAD_REQUEST),
    INVALID_OTP(1014, "auth.otp.invalid", HttpStatus.BAD_REQUEST),
    OTP_ATTEMPT_EXCEEDED(1015, "auth.otp.attempt_exceeded", HttpStatus.TOO_MANY_REQUESTS),
    LOGIN_ATTEMPT_EXCEEDED(1016, "auth.login.attempt_exceeded", HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_NOT_VERIFIED(1017, "auth.email.not_verified", HttpStatus.FORBIDDEN),
    ACCOUNT_BLOCKED(1018, "auth.account.blocked", HttpStatus.FORBIDDEN),
    ACCOUNT_DELETED(1019, "auth.account.deleted", HttpStatus.FORBIDDEN),
    ACCOUNT_PENDING(1020, "auth.account.pending", HttpStatus.FORBIDDEN),

    // ========== User Errors (20xx) ==========
    USER_EXISTED(2001, "auth.username.taken", HttpStatus.CONFLICT),
    USER_NOT_FOUND(2003, "error.user.not_found", HttpStatus.NOT_FOUND),
    EMAIL_EXISTED(2004, "auth.email.taken", HttpStatus.CONFLICT),
    CANNOT_MODIFY_SELF(2005, "error.user.cannot_modify_self", HttpStatus.BAD_REQUEST),
    LAST_ADMIN_PROTECTED(2006, "error.user.last_admin", HttpStatus.BAD_REQUEST),
    INVALID_ROLE(2007, "error.user.invalid_role", HttpStatus.BAD_REQUEST),

    // ========== Category Errors (30xx) ==========
    CATEGORY_NOT_FOUND(3001, "error.category.not_found", HttpStatus.NOT_FOUND),

    // ========== Topic Errors (40xx) ==========
    TOPIC_NOT_FOUND(4001, "error.topic.not_found", HttpStatus.NOT_FOUND),

    // ========== Banner Errors (50xx) ==========
    BANNER_NOT_FOUND(5001, "error.banner.not_found", HttpStatus.NOT_FOUND),

    // ========== Resource Errors (60xx) ==========
    RESOURCE_NOT_FOUND(6001, "error.resource.not_found", HttpStatus.NOT_FOUND),
    FILE_UPLOAD_ERROR(6002, "error.file.upload", HttpStatus.BAD_REQUEST),
    FILE_TYPE_INVALID(6003, "error.file.type.invalid", HttpStatus.BAD_REQUEST),
    RESOURCE_FORBIDDEN(6004, "error.resource.forbidden", HttpStatus.FORBIDDEN),
    INVALID_YOUTUBE_LINK(6005, "error.resource.youtube.invalid", HttpStatus.BAD_REQUEST),
    INVALID_IMAGE_FORMAT(6006, "error.resource.thumbnail.format.invalid", HttpStatus.BAD_REQUEST),
    THUMBNAIL_TOO_LARGE(6007, "error.resource.thumbnail.too.large", HttpStatus.BAD_REQUEST),
    AVATAR_TOO_LARGE(6008, "error.user.avatar.too_large", HttpStatus.BAD_REQUEST),
    RESOURCE_VIEW_RATE_LIMIT_EXCEEDED(6009, "error.resource.view.rate_limit_exceeded", HttpStatus.TOO_MANY_REQUESTS),
    // 410 Gone: tung cong khai, nay da archive/takedown. KHONG dung cho
    // INTERNAL/PRIVATE thieu quyen - nhung truong hop do phai la 404 de
    // khong lo su ton tai (BUSINESS_RULES §4.3).
    RESOURCE_ARCHIVED(6010, "error.resource.archived", HttpStatus.GONE),
    // 401 rieng cho tai file: KHACH khong tai duoc ke ca tai nguyen PUBLIC.
    // Ma rieng de FE hien "Dang nhap de tai tai lieu" ngay tai nut tai,
    // thay vi coi nhu loi phien dang nhap (BUSINESS_RULES §8.4).
    DOWNLOAD_REQUIRES_AUTH(6011, "error.resource.download.requires_auth", HttpStatus.UNAUTHORIZED),

    // ========== Age Group Errors (65xx) ==========
    AGE_GROUP_NOT_FOUND(6501, "error.age_group.not_found", HttpStatus.NOT_FOUND),

    // ========== Report / Notification Errors (66xx) ==========
    REPORT_NOT_FOUND(6601, "error.report.not_found", HttpStatus.NOT_FOUND),
    REPORT_ALREADY_OPEN(6602, "error.report.already_open", HttpStatus.CONFLICT),
    REPORT_ALREADY_RESOLVED(6603, "error.report.already_resolved", HttpStatus.CONFLICT),
    NOTIFICATION_NOT_FOUND(6701, "error.notification.not_found", HttpStatus.NOT_FOUND),

    // ========== Collection Errors (68xx) ==========
    COLLECTION_NOT_FOUND(6801, "error.collection.not_found", HttpStatus.NOT_FOUND),
    COLLECTION_FORBIDDEN(6802, "error.collection.forbidden", HttpStatus.FORBIDDEN),
    COLLECTION_ITEM_EXISTS(6803, "error.collection.item_exists", HttpStatus.CONFLICT),
    COLLECTION_ITEM_NOT_FOUND(6804, "error.collection.item_not_found", HttpStatus.NOT_FOUND),
    COLLECTION_FULL(6805, "error.collection.full", HttpStatus.UNPROCESSABLE_ENTITY),
    COLLECTION_STALE(6806, "error.collection.stale", HttpStatus.CONFLICT),
    COLLECTION_LIMIT_REACHED(6807, "error.collection.limit_reached", HttpStatus.UNPROCESSABLE_ENTITY),

    // ========== Lesson Plan Errors (69xx) ==========
    PLAN_NOT_FOUND(6901, "error.plan.not_found", HttpStatus.NOT_FOUND),
    PLAN_FORBIDDEN(6902, "error.plan.forbidden", HttpStatus.FORBIDDEN),
    PLAN_STALE(6903, "error.plan.stale", HttpStatus.CONFLICT),
    PLAN_LIMIT_REACHED(6904, "error.plan.limit_reached", HttpStatus.UNPROCESSABLE_ENTITY),
    PLAN_REVIEW_STATE(6905, "error.plan.review_state", HttpStatus.CONFLICT),
    PLAN_COMMENT_NOT_FOUND(6906, "error.plan.comment_not_found", HttpStatus.NOT_FOUND),
    PLAN_COMMENT_FORBIDDEN(6907, "error.plan.comment_forbidden", HttpStatus.FORBIDDEN),

    // ========== AI Assistant Errors (71xx) ==========
    AI_DISABLED(7101, "error.ai.disabled", HttpStatus.SERVICE_UNAVAILABLE),
    AI_QUOTA_EXCEEDED(7102, "error.ai.quota_exceeded", HttpStatus.TOO_MANY_REQUESTS),
    AI_UNAVAILABLE(7103, "error.ai.unavailable", HttpStatus.BAD_GATEWAY),
    AI_REFUSED(7104, "error.ai.refused", HttpStatus.UNPROCESSABLE_ENTITY),

    // ========== Duplicate Errors (70xx) ==========
    DUPLICATE_SLUG(7001, "error.duplicate.slug", HttpStatus.CONFLICT),
    DUPLICATE_NAME(7002, "error.duplicate.name", HttpStatus.CONFLICT),
    DUPLICATE_ENTRY(7003, "error.duplicate.entry", HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(7004, "error.concurrent.modification", HttpStatus.CONFLICT),

    // ========== System Errors (80xx) ==========
    FIREBASE_INIT_ERROR(8001, "error.firebase.init", HttpStatus.INTERNAL_SERVER_ERROR),
    STORAGE_ERROR(8002, "error.storage", HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_SERVICE_ERROR(8003, "error.external.service", HttpStatus.INTERNAL_SERVER_ERROR),
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
