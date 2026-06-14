package com.kindergarten.warehouse.util;

/**
 * Các regex validation dùng chung BE (và đồng bộ với FE theo API_CONTRACT_V1).
 */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /**
     * Policy mật khẩu (D3 / [SEC-5]): tối thiểu 8 ký tự, bắt buộc có chữ hoa,
     * chữ thường và chữ số. Dùng chung cho register / reset-password /
     * change-password. FE phải dùng cùng regex này.
     */
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";
}
