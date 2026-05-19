package com.kindergarten.warehouse.entity;

public enum UserStatus {
    /** Chờ xác thực email (mới đăng ký) */
    PENDING,
    /** Hoạt động bình thường */
    ACTIVE,
    /** Bị admin khóa */
    BLOCKED,
    /** Không hoạt động lâu (tự động hoặc admin thao tác) */
    INACTIVE
}
