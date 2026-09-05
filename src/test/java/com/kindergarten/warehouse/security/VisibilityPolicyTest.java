package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static com.kindergarten.warehouse.entity.Visibility.INTERNAL;
import static com.kindergarten.warehouse.entity.Visibility.PRIVATE;
import static com.kindergarten.warehouse.entity.Visibility.PUBLIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phủ toàn bộ ma trận visibility của BUSINESS_RULES_V1 §3.2, §3.3 và §8.4.
 *
 * <p>Đây là lưới an toàn phải có TRƯỚC khi thay 22 phép so sánh
 * {@code == Visibility.PUBLIC} rải rác trong code.
 */
class VisibilityPolicyTest {

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_ID = 99L;

    private static Viewer guest() {
        return Viewer.guest();
    }

    private static Viewer user() {
        return Viewer.of(OTHER_ID, Set.of(Role.USER));
    }

    private static Viewer otherTeacher() {
        return Viewer.of(OTHER_ID, Set.of(Role.TEACHER));
    }

    private static Viewer owner() {
        return Viewer.of(OWNER_ID, Set.of(Role.TEACHER));
    }

    private static Viewer admin() {
        return Viewer.of(123L, Set.of(Role.ADMIN));
    }

    // =====================================================================
    // §3.2 — visibility hiệu lực là mức chặt nhất của Category → Topic → Resource
    // =====================================================================

    @Nested
    @DisplayName("§3.2 visibility hiệu lực")
    class EffectiveVisibility {

        /** Đúng sáu dòng ví dụ ghi trong BUSINESS_RULES §3.2. */
        @ParameterizedTest(name = "cat={0} topic={1} res={2} -> {3}")
        @CsvSource({
                "PUBLIC,   PUBLIC,   PUBLIC,   PUBLIC",
                "PUBLIC,   INTERNAL, PUBLIC,   INTERNAL",
                "PUBLIC,   PUBLIC,   INTERNAL, INTERNAL",
                "INTERNAL, PUBLIC,   PUBLIC,   INTERNAL",
                "PUBLIC,   PUBLIC,   PRIVATE,  PRIVATE",
                "PRIVATE,  PUBLIC,   PUBLIC,   PRIVATE",
        })
        void matchesDocumentedExamples(Visibility category, Visibility topic,
                                       Visibility resource, Visibility expected) {
            assertEquals(expected,
                    VisibilityPolicy.effectiveVisibility(category, topic, resource));
        }

        /** Cả 27 tổ hợp: kết quả luôn là mức chặt nhất, không phụ thuộc vị trí. */
        @Test
        void allTwentySevenCombinationsTakeTheMostRestrictive() {
            for (Visibility category : Visibility.values()) {
                for (Visibility topic : Visibility.values()) {
                    for (Visibility resource : Visibility.values()) {
                        Visibility expected = maxRestriction(category, topic, resource);
                        assertEquals(expected,
                                VisibilityPolicy.effectiveVisibility(category, topic, resource),
                                "cat=" + category + " topic=" + topic + " res=" + resource);
                    }
                }
            }
        }

        private Visibility maxRestriction(Visibility a, Visibility b, Visibility c) {
            Visibility result = a;
            if (b.getRestrictionLevel() > result.getRestrictionLevel()) result = b;
            if (c.getRestrictionLevel() > result.getRestrictionLevel()) result = c;
            return result;
        }

        @Test
        @DisplayName("fail-closed: mắt xích null tính là PRIVATE")
        void nullLinkIsTreatedAsPrivate() {
            assertEquals(PRIVATE, VisibilityPolicy.effectiveVisibility(null, PUBLIC, PUBLIC));
            assertEquals(PRIVATE, VisibilityPolicy.effectiveVisibility(PUBLIC, null, PUBLIC));
            assertEquals(PRIVATE, VisibilityPolicy.effectiveVisibility(PUBLIC, PUBLIC, null));
        }
    }

    // =====================================================================
    // §3.3 — ai xem được gì
    // =====================================================================

    @Nested
    @DisplayName("§3.3 quyền xem")
    class CanView {

        @Test
        @DisplayName("PUBLIC: mọi vai trò đều xem được")
        void publicIsVisibleToEveryone() {
            assertTrue(VisibilityPolicy.canView(guest(), PUBLIC, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(user(), PUBLIC, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(otherTeacher(), PUBLIC, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(owner(), PUBLIC, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(admin(), PUBLIC, OWNER_ID));
        }

        @Test
        @DisplayName("INTERNAL: khách KHÔNG xem được, người đã đăng nhập thì có")
        void internalHidesGuestOnly() {
            assertFalse(VisibilityPolicy.canView(guest(), INTERNAL, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(user(), INTERNAL, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(otherTeacher(), INTERNAL, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(owner(), INTERNAL, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(admin(), INTERNAL, OWNER_ID));
        }

        @Test
        @DisplayName("PRIVATE: chỉ chủ sở hữu và ADMIN")
        void privateIsOwnerAndAdminOnly() {
            assertFalse(VisibilityPolicy.canView(guest(), PRIVATE, OWNER_ID));
            assertFalse(VisibilityPolicy.canView(user(), PRIVATE, OWNER_ID));
            assertFalse(VisibilityPolicy.canView(otherTeacher(), PRIVATE, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(owner(), PRIVATE, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(admin(), PRIVATE, OWNER_ID));
        }

        @Test
        @DisplayName("TEACHER khác không được xem PRIVATE của người khác")
        void otherTeacherIsNotAnOwner() {
            assertFalse(VisibilityPolicy.canView(otherTeacher(), PRIVATE, OWNER_ID));
        }

        @Test
        @DisplayName("ADMIN xem được mọi mức, kể cả không phải chủ")
        void adminSeesEverything() {
            for (Visibility v : Visibility.values()) {
                assertTrue(VisibilityPolicy.canView(admin(), v, OWNER_ID), "mức " + v);
            }
        }

        @Test
        @DisplayName("fail-closed: visibility null thì không ai ngoài admin/chủ xem được")
        void nullVisibilityDeniesEveryoneElse() {
            assertFalse(VisibilityPolicy.canView(guest(), null, OWNER_ID));
            assertFalse(VisibilityPolicy.canView(user(), null, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(owner(), null, OWNER_ID));
            assertTrue(VisibilityPolicy.canView(admin(), null, OWNER_ID));
        }

        @Test
        @DisplayName("ownerId null: không ai là chủ")
        void nullOwnerMeansNobodyOwnsIt() {
            assertFalse(VisibilityPolicy.canView(owner(), PRIVATE, null));
            assertTrue(VisibilityPolicy.canView(admin(), PRIVATE, null));
        }

        @Test
        @DisplayName("viewer null là lỗi lập trình, không phải khách")
        void nullViewerIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> VisibilityPolicy.canView(null, PUBLIC, OWNER_ID));
        }

        @Test
        @DisplayName("overload ba mắt xích khớp với gọi hai bước")
        void chainOverloadMatchesTwoStepCall() {
            for (Visibility cat : Visibility.values()) {
                for (Visibility topic : Visibility.values()) {
                    for (Visibility res : Visibility.values()) {
                        boolean viaChain =
                                VisibilityPolicy.canView(user(), cat, topic, res, OWNER_ID);
                        boolean viaEffective = VisibilityPolicy.canView(user(),
                                VisibilityPolicy.effectiveVisibility(cat, topic, res), OWNER_ID);
                        assertEquals(viaEffective, viaChain,
                                "cat=" + cat + " topic=" + topic + " res=" + res);
                    }
                }
            }
        }
    }

    // =====================================================================
    // §8.4 — tải file chặt hơn xem đúng một bậc
    // =====================================================================

    @Nested
    @DisplayName("§8.4 quyền tải file")
    class CanDownload {

        @ParameterizedTest(name = "khách không tải được dù mức {0}")
        @EnumSource(Visibility.class)
        void guestCanNeverDownload(Visibility visibility) {
            assertFalse(VisibilityPolicy.canDownload(guest(), visibility, OWNER_ID));
        }

        @Test
        @DisplayName("khách xem được PUBLIC nhưng vẫn không tải được")
        void guestSeesPublicButCannotDownloadIt() {
            assertTrue(VisibilityPolicy.canView(guest(), PUBLIC, OWNER_ID));
            assertFalse(VisibilityPolicy.canDownload(guest(), PUBLIC, OWNER_ID));
        }

        @Test
        @DisplayName("đã đăng nhập thì quyền tải bám theo quyền xem")
        void authenticatedDownloadFollowsView() {
            assertTrue(VisibilityPolicy.canDownload(user(), PUBLIC, OWNER_ID));
            assertTrue(VisibilityPolicy.canDownload(user(), INTERNAL, OWNER_ID));
            assertFalse(VisibilityPolicy.canDownload(user(), PRIVATE, OWNER_ID));

            assertTrue(VisibilityPolicy.canDownload(owner(), PRIVATE, OWNER_ID));
            assertTrue(VisibilityPolicy.canDownload(admin(), PRIVATE, OWNER_ID));
        }
    }

    // =====================================================================
    // Bất biến của enum
    // =====================================================================

    @Nested
    @DisplayName("thứ tự mức chặt")
    class Ordering {

        @Test
        void publicIsLoosestAndPrivateIsStrictest() {
            assertTrue(INTERNAL.isMoreRestrictiveThan(PUBLIC));
            assertTrue(PRIVATE.isMoreRestrictiveThan(INTERNAL));
            assertTrue(PRIVATE.isMoreRestrictiveThan(PUBLIC));

            assertFalse(PUBLIC.isMoreRestrictiveThan(PUBLIC));
            assertFalse(PUBLIC.isMoreRestrictiveThan(INTERNAL));
        }

        @Test
        @DisplayName("mostRestrictive không phụ thuộc thứ tự tham số")
        void mostRestrictiveIsOrderIndependent() {
            assertEquals(PRIVATE, Visibility.mostRestrictive(PUBLIC, PRIVATE, INTERNAL));
            assertEquals(PRIVATE, Visibility.mostRestrictive(PRIVATE, INTERNAL, PUBLIC));
            assertEquals(INTERNAL, Visibility.mostRestrictive(INTERNAL, PUBLIC));
        }

        @Test
        @DisplayName("fail-closed: không có tham số nào thì là PRIVATE")
        void emptyOrNullIsPrivate() {
            assertEquals(PRIVATE, Visibility.mostRestrictive());
            assertEquals(PRIVATE, Visibility.mostRestrictive((Visibility[]) null));
        }
    }
}
