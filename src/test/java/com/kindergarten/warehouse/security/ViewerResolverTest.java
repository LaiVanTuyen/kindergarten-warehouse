package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerResolverTest {

    private ViewerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ViewerResolver();
    }

    private static CustomUserDetails principal(Long id, Set<String> roles) {
        return CustomUserDetails.builder()
                .id(id)
                .username("nguoidung")
                .email("nguoidung@example.com")
                .roles(roles)
                .enabled(true)
                .emailVerified(true)
                .tokenVersion(0L)
                .build();
    }

    /**
     * Cố ý dựng token với danh sách authority RỖNG.
     *
     * <p>{@link ViewerResolver} đọc {@code principal.getRoles()}, không đọc
     * {@code authentication.getAuthorities()}. Nếu ở đây gọi
     * {@code details.getAuthorities()} thì test sẽ vỡ ngay khi dựng token với
     * role rỗng — vì {@code SimpleGrantedAuthority} từ chối chuỗi rỗng — và ta
     * sẽ tưởng resolver có lỗi trong khi nó chưa hề được gọi.
     */
    private static Authentication authenticated(CustomUserDetails details) {
        return new UsernamePasswordAuthenticationToken(details, null, List.of());
    }

    // =====================================================================

    @Nested
    @DisplayName("chưa đăng nhập thì là khách")
    class GuestCases {

        @Test
        @DisplayName("authentication null → guest")
        void nullAuthenticationIsGuest() {
            Viewer viewer = resolver.resolve(null);

            assertFalse(viewer.isAuthenticated());
            assertFalse(viewer.isAdmin());
            assertSame(Viewer.guest(), viewer);
        }

        @Test
        @DisplayName("anonymous token → guest")
        void anonymousIsGuest() {
            Authentication anonymous = new AnonymousAuthenticationToken(
                    "key",
                    "anonymousUser",
                    List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

            // Bẫy dễ mắc: AnonymousAuthenticationToken.isAuthenticated() trả true,
            // nên bắt buộc phải kiểm riêng bằng instanceof.
            assertTrue(anonymous.isAuthenticated());

            Viewer viewer = resolver.resolve(anonymous);

            assertFalse(viewer.isAuthenticated());
            assertSame(Viewer.guest(), viewer);
        }

        @Test
        @DisplayName("token chưa xác thực → guest")
        void unauthenticatedTokenIsGuest() {
            TestingAuthenticationToken token =
                    new TestingAuthenticationToken("nguoidung", "matkhau");
            token.setAuthenticated(false);

            Viewer viewer = resolver.resolve(token);

            assertFalse(viewer.isAuthenticated());
            assertSame(Viewer.guest(), viewer);
        }
    }

    // =====================================================================

    @Nested
    @DisplayName("người dùng hợp lệ")
    class ValidPrincipal {

        @Test
        @DisplayName("lấy đúng userId và roles")
        void mapsIdAndRoles() {
            Viewer viewer = resolver.resolve(
                    authenticated(principal(42L, Set.of("TEACHER", "USER"))));

            assertTrue(viewer.isAuthenticated());
            assertEquals(42L, viewer.userId());
            assertEquals(Set.of(Role.TEACHER, Role.USER), viewer.roles());
            assertFalse(viewer.isAdmin());
        }

        @Test
        @DisplayName("ADMIN được nhận diện")
        void adminIsRecognised() {
            Viewer viewer = resolver.resolve(
                    authenticated(principal(1L, Set.of("ADMIN", "TEACHER"))));

            assertTrue(viewer.isAdmin());
            assertEquals(Set.of(Role.ADMIN, Role.TEACHER), viewer.roles());
        }

        @Test
        @DisplayName("roles null → tập rỗng, không phải lỗi")
        void nullRolesBecomeEmptySet() {
            Viewer viewer = resolver.resolve(authenticated(principal(5L, null)));

            assertTrue(viewer.isAuthenticated());
            assertFalse(viewer.isAdmin());
            assertTrue(viewer.roles().isEmpty());
        }
    }

    // =====================================================================

    @Nested
    @DisplayName("chuẩn hoá tên role")
    class RoleNormalisation {

        @ParameterizedTest(name = "\"{0}\" → Role.ADMIN")
        @ValueSource(strings = { "ADMIN", "ROLE_ADMIN", "admin", "role_admin", "  ADMIN  " })
        void acceptsBareAndPrefixedForms(String raw) {
            Viewer viewer = resolver.resolve(authenticated(principal(1L, Set.of(raw))));

            assertTrue(viewer.isAdmin(), "khong nhan dien duoc: " + raw);
        }

        @Test
        @DisplayName("role lạ bị bỏ qua, KHÔNG cấp quyền")
        void unknownRoleIsIgnored() {
            Viewer viewer = resolver.resolve(
                    authenticated(principal(9L, Set.of("SUPER_ADMIN", "ROOT", "ADMINISTRATOR"))));

            assertTrue(viewer.isAuthenticated());
            assertFalse(viewer.isAdmin(), "role la khong duoc nang thanh ADMIN");
            assertTrue(viewer.roles().isEmpty());
        }

        @Test
        @DisplayName("trộn role hợp lệ và role lạ: chỉ giữ cái hợp lệ")
        void keepsOnlyKnownRoles() {
            Viewer viewer = resolver.resolve(
                    authenticated(principal(9L, Set.of("TEACHER", "SUPER_ADMIN"))));

            assertEquals(Set.of(Role.TEACHER), viewer.roles());
            assertFalse(viewer.isAdmin());
        }

        @Test
        @DisplayName("chuỗi rỗng hoặc toàn khoảng trắng bị bỏ qua")
        void blankRoleIsIgnored() {
            Viewer viewer = resolver.resolve(
                    authenticated(principal(9L, Set.of("", "   ", "USER"))));

            assertEquals(Set.of(Role.USER), viewer.roles());
        }
    }

    // =====================================================================

    @Nested
    @DisplayName("principal sai kiểu là lỗi lập trình")
    class WrongPrincipalType {

        @Test
        @DisplayName("principal là String → IllegalStateException, không hạ thành guest")
        void stringPrincipalThrows() {
            Authentication token = new UsernamePasswordAuthenticationToken(
                    "chuoi-nguoi-dung", null, List.of());

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> resolver.resolve(token));

            assertTrue(ex.getMessage().contains("String"), ex.getMessage());
        }

        @Test
        @DisplayName("âm thầm biến thành guest sẽ che mất lỗi cấu hình")
        void doesNotSilentlyDowngradeToGuest() {
            Authentication token = new UsernamePasswordAuthenticationToken(
                    Integer.valueOf(1), null, List.of());

            assertThrows(IllegalStateException.class, () -> resolver.resolve(token));
        }
    }
}
