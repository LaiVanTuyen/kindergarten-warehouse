package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Role;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Người đang xem, rút gọn về đúng những gì {@link VisibilityPolicy} cần biết:
 * họ là ai và có vai trò gì.
 *
 * <p>Cố ý <strong>không</strong> phụ thuộc vào Spring Security, HttpServletRequest
 * hay entity {@code User}. Nhờ vậy policy kiểm thử được bằng unit test thuần và
 * dùng lại được ở mọi tầng — service, specification, hay bộ lọc.
 *
 * <p>Khách chưa đăng nhập biểu diễn bằng {@link #guest()}, không phải {@code null}.
 * Truyền {@code null} vào policy là lỗi lập trình, không phải "khách".
 */
public record Viewer(Long userId, Set<Role> roles) {

    private static final Viewer GUEST = new Viewer(null, Set.of());

    public Viewer {
        roles = (roles == null || roles.isEmpty())
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    /** Khách chưa đăng nhập. */
    public static Viewer guest() {
        return GUEST;
    }

    public static Viewer of(Long userId, Set<Role> roles) {
        return new Viewer(userId, roles);
    }

    public boolean isAuthenticated() {
        return userId != null;
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    /**
     * Người này có phải chủ sở hữu của tài nguyên do {@code ownerId} chỉ định.
     *
     * <p>Trả {@code false} khi chưa đăng nhập hoặc khi {@code ownerId} là
     * {@code null} — tài nguyên không có chủ thì không ai là chủ.
     */
    public boolean owns(Long ownerId) {
        return userId != null && ownerId != null && userId.equals(ownerId);
    }
}
