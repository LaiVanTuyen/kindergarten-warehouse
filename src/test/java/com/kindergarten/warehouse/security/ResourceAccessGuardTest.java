package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phủ tám nhóm bắt buộc của pipeline detail/slug, cộng kiểm tra visibility
 * phân tầng (BUSINESS_RULES_V1 §3.2, §3.3, §4.3).
 *
 * <p>Trọng tâm là <strong>phân biệt 404 với 410</strong>: không có quyền thì
 * luôn 404, kể cả khi tài nguyên đã archive — nếu không sẽ lộ sự tồn tại.
 */
class ResourceAccessGuardTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 20L;

    private ResourceAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ResourceAccessGuard();
    }

    // --- dựng dữ liệu -----------------------------------------------------

    private Resource resource(Visibility categoryVis, Visibility topicVis,
                              Visibility resourceVis, ResourceStatus status) {
        Category category = new Category();
        category.setIsDeleted(false);
        category.setVisibility(categoryVis);

        Topic topic = new Topic();
        topic.setIsDeleted(false);
        topic.setVisibility(topicVis);
        topic.setCategory(category);

        Resource resource = new Resource();
        resource.setId("res-1");
        resource.setSlug("tai-lieu-mau");
        resource.setTopic(topic);
        resource.setVisibility(resourceVis);
        resource.setStatus(status);
        resource.setIsDeleted(false);
        resource.setCreatedBy(OWNER_ID);
        return resource;
    }

    private Resource approved(Visibility visibility) {
        return resource(Visibility.PUBLIC, Visibility.PUBLIC, visibility, ResourceStatus.APPROVED);
    }

    private static Viewer guest() {
        return Viewer.guest();
    }

    private static Viewer owner() {
        return Viewer.of(OWNER_ID, Set.of(Role.TEACHER));
    }

    private static Viewer otherTeacher() {
        return Viewer.of(OTHER_ID, Set.of(Role.TEACHER));
    }

    private static Viewer admin() {
        return Viewer.of(99L, Set.of(Role.ADMIN));
    }

    private void assertNotFound(Resource resource, Viewer viewer) {
        AppException ex = assertThrows(AppException.class,
                () -> guard.requireViewable(resource, viewer));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    private void assertGone(Resource resource, Viewer viewer) {
        AppException ex = assertThrows(AppException.class,
                () -> guard.requireViewable(resource, viewer));
        assertEquals(ErrorCode.RESOURCE_ARCHIVED, ex.getErrorCode());
    }

    // =====================================================================
    // Tám nhóm bắt buộc
    // =====================================================================

    @Test
    @DisplayName("1. Guest + PUBLIC + active → xem được")
    void guestSeesPublicActive() {
        Resource resource = approved(Visibility.PUBLIC);
        assertSame(resource, guard.requireViewable(resource, guest()));
    }

    @Test
    @DisplayName("2. Guest + INTERNAL/PRIVATE → 404")
    void guestGetsNotFoundForNonPublic() {
        assertNotFound(approved(Visibility.INTERNAL), guest());
        assertNotFound(approved(Visibility.PRIVATE), guest());
    }

    @Test
    @DisplayName("3. Chủ sở hữu xem được INTERNAL và PRIVATE của chính mình")
    void ownerSeesOwnRestrictedResources() {
        Resource internal = approved(Visibility.INTERNAL);
        Resource priv = approved(Visibility.PRIVATE);

        assertSame(internal, guard.requireViewable(internal, owner()));
        assertSame(priv, guard.requireViewable(priv, owner()));
    }

    @Test
    @DisplayName("4. Teacher khác + PRIVATE của người khác → 404")
    void otherTeacherCannotSeePrivate() {
        assertNotFound(approved(Visibility.PRIVATE), otherTeacher());
    }

    @Test
    @DisplayName("4b. Teacher khác vẫn xem được INTERNAL (chỉ cần đăng nhập)")
    void otherTeacherSeesInternal() {
        Resource internal = approved(Visibility.INTERNAL);
        assertSame(internal, guard.requireViewable(internal, otherTeacher()));
    }

    @Test
    @DisplayName("5. Admin + PRIVATE → xem được")
    void adminSeesPrivate() {
        Resource priv = approved(Visibility.PRIVATE);
        assertSame(priv, guard.requireViewable(priv, admin()));
    }

    @Test
    @DisplayName("6. Có quyền + ARCHIVED → 410")
    void permittedViewerGetsGoneForArchived() {
        Resource archivedPublic = resource(Visibility.PUBLIC, Visibility.PUBLIC,
                Visibility.PUBLIC, ResourceStatus.ARCHIVED);

        assertGone(archivedPublic, guest());
        assertGone(archivedPublic, otherTeacher());
        assertGone(archivedPublic, admin());
    }

    @Test
    @DisplayName("7. KHÔNG có quyền + ARCHIVED → 404, không phải 410")
    void unauthorisedViewerGetsNotFoundForArchived() {
        Resource archivedInternal = resource(Visibility.PUBLIC, Visibility.PUBLIC,
                Visibility.INTERNAL, ResourceStatus.ARCHIVED);
        Resource archivedPrivate = resource(Visibility.PUBLIC, Visibility.PUBLIC,
                Visibility.PRIVATE, ResourceStatus.ARCHIVED);

        // Trả 410 ở đây là lộ sự tồn tại của tài nguyên nội bộ.
        assertNotFound(archivedInternal, guest());
        assertNotFound(archivedPrivate, guest());
        assertNotFound(archivedPrivate, otherTeacher());
    }

    @Test
    @DisplayName("8. Không tồn tại hoặc đã xoá mềm → 404 cho mọi vai trò")
    void missingOrSoftDeletedIsNotFound() {
        assertNotFound(null, guest());
        assertNotFound(null, admin());

        Resource deleted = approved(Visibility.PUBLIC);
        deleted.setIsDeleted(true);

        assertNotFound(deleted, guest());
        assertNotFound(deleted, owner());
        assertNotFound(deleted, admin());
    }

    // =====================================================================
    // Visibility phân tầng
    // =====================================================================

    @Nested
    @DisplayName("visibility phân tầng Category → Topic → Resource")
    class CascadingVisibility {

        @Test
        @DisplayName("Resource PUBLIC nhưng Topic INTERNAL → khách không thấy")
        void internalTopicHidesPublicResource() {
            Resource r = resource(Visibility.PUBLIC, Visibility.INTERNAL,
                    Visibility.PUBLIC, ResourceStatus.APPROVED);

            assertEquals(Visibility.INTERNAL, guard.effectiveVisibilityOf(r));
            assertNotFound(r, guest());
            assertSame(r, guard.requireViewable(r, otherTeacher()));
        }

        @Test
        @DisplayName("Resource PUBLIC nhưng Category PRIVATE → chỉ chủ và admin")
        void privateCategoryHidesPublicResource() {
            Resource r = resource(Visibility.PRIVATE, Visibility.PUBLIC,
                    Visibility.PUBLIC, ResourceStatus.APPROVED);

            assertEquals(Visibility.PRIVATE, guard.effectiveVisibilityOf(r));
            assertNotFound(r, guest());
            assertNotFound(r, otherTeacher());
            assertSame(r, guard.requireViewable(r, owner()));
            assertSame(r, guard.requireViewable(r, admin()));
        }

        @Test
        @DisplayName("Topic bị xoá mềm → fail-closed thành PRIVATE")
        void deletedTopicIsFailClosed() {
            Resource r = approved(Visibility.PUBLIC);
            r.getTopic().setIsDeleted(true);

            assertEquals(Visibility.PRIVATE, guard.effectiveVisibilityOf(r));
            assertNotFound(r, guest());
            assertSame(r, guard.requireViewable(r, admin()));
        }

        @Test
        @DisplayName("Category bị xoá mềm → fail-closed thành PRIVATE")
        void deletedCategoryIsFailClosed() {
            Resource r = approved(Visibility.PUBLIC);
            r.getTopic().getCategory().setIsDeleted(true);

            assertEquals(Visibility.PRIVATE, guard.effectiveVisibilityOf(r));
            assertNotFound(r, guest());
        }

        @Test
        @DisplayName("Topic null → fail-closed thành PRIVATE, không ném NPE")
        void nullTopicIsFailClosed() {
            Resource r = approved(Visibility.PUBLIC);
            r.setTopic(null);

            assertEquals(Visibility.PRIVATE, guard.effectiveVisibilityOf(r));
            assertNotFound(r, guest());
        }
    }

    // =====================================================================
    // status là cổng độc lập với visibility
    // =====================================================================

    @Nested
    @DisplayName("status chưa duyệt")
    class UnapprovedStatus {

        @ParameterizedTest(name = "{0} + PUBLIC: khách vẫn không thấy")
        @EnumSource(value = ResourceStatus.class, names = { "DRAFT", "PENDING", "REJECTED" })
        void unapprovedIsHiddenFromPublic(ResourceStatus status) {
            Resource r = resource(Visibility.PUBLIC, Visibility.PUBLIC, Visibility.PUBLIC, status);

            // Có quyền theo visibility nhưng status chặn lại — hai cổng độc lập.
            assertNotFound(r, guest());
            assertNotFound(r, otherTeacher());
        }

        @ParameterizedTest(name = "{0}: chủ sở hữu và admin vẫn xem được")
        @EnumSource(value = ResourceStatus.class, names = { "DRAFT", "PENDING", "REJECTED" })
        void ownerAndAdminCanStillSee(ResourceStatus status) {
            Resource r = resource(Visibility.PUBLIC, Visibility.PUBLIC, Visibility.PUBLIC, status);

            assertSame(r, guard.requireViewable(r, owner()));
            assertSame(r, guard.requireViewable(r, admin()));
        }
    }

    // =====================================================================
    // Tải file chặt hơn xem một bậc
    // =====================================================================

    @Nested
    @DisplayName("requireDownloadable")
    class Download {

        @Test
        @DisplayName("DRAFT không tải được kể cả bởi chủ sở hữu")
        void ownerCannotDownloadDraft() {
            Resource resource = resource(
                    Visibility.PUBLIC, Visibility.PUBLIC, Visibility.PRIVATE, ResourceStatus.DRAFT);

            AppException exception = assertThrows(AppException.class,
                    () -> guard.requireDownloadable(resource, owner()));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("khách xem được PUBLIC nhưng tải thì nhận 401 mã 6011")
        void guestCannotDownloadPublic() {
            Resource r = approved(Visibility.PUBLIC);

            assertSame(r, guard.requireViewable(r, guest()));

            AppException ex = assertThrows(AppException.class,
                    () -> guard.requireDownloadable(r, guest()));
            assertEquals(ErrorCode.DOWNLOAD_REQUIRES_AUTH, ex.getErrorCode());
        }

        @Test
        @DisplayName("khách tải tài nguyên không tồn tại → 404, KHÔNG phải 401")
        void guestDownloadingMissingResourceGetsNotFound() {
            // Trả 401 trước sẽ cho biết id nào có thật.
            AppException ex = assertThrows(AppException.class,
                    () -> guard.requireDownloadable(null, guest()));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("khách tải tài nguyên INTERNAL → 404, KHÔNG phải 401")
        void guestDownloadingInternalGetsNotFound() {
            AppException ex = assertThrows(AppException.class,
                    () -> guard.requireDownloadable(approved(Visibility.INTERNAL), guest()));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("đã đăng nhập thì tải theo đúng quyền xem")
        void authenticatedDownloadFollowsView() {
            Resource pub = approved(Visibility.PUBLIC);
            Resource priv = approved(Visibility.PRIVATE);

            assertSame(pub, guard.requireDownloadable(pub, otherTeacher()));
            assertSame(priv, guard.requireDownloadable(priv, owner()));
            assertSame(priv, guard.requireDownloadable(priv, admin()));

            AppException ex = assertThrows(AppException.class,
                    () -> guard.requireDownloadable(priv, otherTeacher()));
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        }
    }
}
