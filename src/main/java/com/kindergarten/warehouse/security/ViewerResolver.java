package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Adapter biên: chuyển {@link Authentication} của Spring Security thành
 * {@link Viewer} thuần.
 *
 * <p>Đây là <strong>nơi duy nhất</strong> Spring Security gặp mô hình quyền của
 * ứng dụng. {@link Viewer} và {@link VisibilityPolicy} cố ý không biết gì về
 * Spring, nhờ vậy kiểm thử được bằng unit test thuần.
 *
 * <p><strong>Nhận {@code Authentication} từ tham số, không tự đọc
 * {@code SecurityContextHolder}.</strong> Controller lấy {@code Viewer} qua
 * resolver rồi truyền tường minh xuống service. Cách này khiến phụ thuộc hiện
 * rõ trong chữ ký hàm, test dễ hơn, và tránh chôn security context sâu trong
 * tầng nghiệp vụ. Nếu sau này nhiều nơi cần người dùng hiện tại thì có thể thêm
 * {@code resolveCurrent()}, nhưng {@link #resolve(Authentication)} vẫn là lõi.
 */
@Slf4j
@Component
public class ViewerResolver {

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * @param authentication có thể {@code null}
     * @return {@link Viewer#guest()} nếu chưa đăng nhập; ngược lại là người dùng thật
     * @throws IllegalStateException khi đã xác thực nhưng principal không phải
     *         {@link CustomUserDetails} — đó là lỗi cấu hình, không được âm thầm
     *         hạ xuống thành khách
     */
    public Viewer resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Viewer.guest();
        }

        if (!(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            throw new IllegalStateException(
                    "Principal da xac thuc nhung sai kieu: "
                            + (authentication.getPrincipal() == null
                                    ? "null"
                                    : authentication.getPrincipal().getClass().getName()));
        }

        return new Viewer(principal.getId(), mapRoles(principal.getRoles()));
    }

    /**
     * Chuyển tên role dạng chuỗi sang {@link Role}.
     *
     * <p>Role không nhận diện được thì <strong>bỏ qua và ghi cảnh báo</strong>,
     * không cấp quyền. Ném lỗi ở đây sẽ khiến một role rác trong token làm hỏng
     * toàn bộ request; cấp quyền cho nó thì còn tệ hơn.
     */
    private Set<Role> mapRoles(Set<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            return Set.of();
        }

        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String raw : rawRoles) {
            Role mapped = toRole(raw);
            if (mapped == null) {
                log.warn("[ViewerResolver] Bo qua role khong nhan dien: '{}'", raw);
            } else {
                roles.add(mapped);
            }
        }
        return roles;
    }

    /**
     * Map tường minh, không dùng {@code Role.valueOf}.
     *
     * <p>Hai lý do: {@code valueOf} ném lỗi với giá trị lạ, và nó sẽ tự động
     * chấp nhận mọi hằng số thêm vào {@link Role} sau này. Danh sách tường minh
     * buộc người thêm role mới phải cân nhắc ảnh hưởng tới visibility.
     *
     * <p>Chấp nhận cả {@code ADMIN} lẫn {@code ROLE_ADMIN}. Hiện
     * {@code CustomUserDetailsService} sinh dạng trần ({@code Role::name}), nên
     * nhánh có tiền tố là phòng thủ cho token cũ hoặc nguồn khác.
     */
    private Role toRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(ROLE_PREFIX)) {
            normalized = normalized.substring(ROLE_PREFIX.length());
        }

        return switch (normalized) {
            case "ADMIN" -> Role.ADMIN;
            case "TEACHER" -> Role.TEACHER;
            case "USER" -> Role.USER;
            default -> null;
        };
    }
}
