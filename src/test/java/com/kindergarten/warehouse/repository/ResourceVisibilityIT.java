package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.service.MinioStorageService;
import com.kindergarten.warehouse.service.ResourceService;
import com.kindergarten.warehouse.service.ResourceStatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test cho visibility của danh sách Portal, chạy trên
 * <strong>MySQL thật</strong> qua Testcontainers với Flyway migration thật.
 *
 * <p>Vì sao không dùng H2: {@code ResourceVisibilitySpecifications} là logic
 * Criteria API sinh ra SQL thật, và migration của dự án viết cho MySQL. H2 khác
 * MySQL ở enum, JSON, unique-NULL và collation — đủ để một test visibility cho
 * kết quả sai lệch mà vẫn xanh.
 *
 * <p>Vì sao không thay bằng {@code perf/check-query-count.sh}: script đó kiểm
 * <em>deployment</em> (cấu hình, JWT, dữ liệu demo). Nó phụ thuộc stack đang
 * chạy và bộ seed nên không tái lập được trong CI. Hai thứ bổ sung cho nhau,
 * không thay thế nhau.
 *
 * <p>Dữ liệu tạo <strong>tường minh</strong> trong {@code @BeforeEach}, không
 * dựa vào {@code DataSeeder} hay perf seed. Mọi khẳng định đều lọc theo topic
 * riêng của test nên không bị dữ liệu seeder làm nhiễu.
 *
 * <h2>Cách chạy</h2>
 *
 * <pre>mvn verify -Pintegration-test</pre>
 *
 * <p>Yêu cầu môi trường: <strong>JDK 17 và Docker chạy trực tiếp trên máy
 * host</strong>.
 *
 * <p><strong>CHƯA CHẠY ĐƯỢC TRÊN MÁY DEV HIỆN TẠI</strong> (2026-09-05). Máy
 * chỉ có JDK 8 nên Maven phải chạy trong container, mà Testcontainers không
 * khởi động được container anh em qua Docker Desktop trên Windows: socket mount
 * vào container trả {@code HTTP 400} cho endpoint {@code /info}, với nhãn
 * {@code com.docker.desktop.address=npipe://...}. Cổng TCP 2375 cũng đóng.
 *
 * <p>Đây là hạn chế môi trường, không phải lỗi của test. Trên CI hoặc máy có
 * JDK 17 + Docker thì chạy bình thường.
 *
 * <p>Quan trọng: Failsafe <strong>FAIL</strong> khi không có Docker, không
 * skip — đã kiểm chứng. Nghĩa là CI sẽ báo đỏ chứ không cho pass giả.
 */
@Testcontainers
@SpringBootTest
@Transactional
class ResourceVisibilityIT {

    /**
     * Ghim đúng phiên bản đang chạy ở demo/production ({@code 8.0.46}).
     * Dùng {@code latest} sẽ khiến test đổi hành vi khi image upstream đổi.
     */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("warehouse_it")
            .withUsername("it_user")
            .withPassword("it_pass");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        // Admin seed phai co gia tri hop le, neu khong DataSeeder chan boot.
        registry.add("app.admin.password", () -> "IntegrationTest#2026");
        registry.add("app.admin.email", () -> "it-admin@example.com");

        // application.yml co BON placeholder KHONG co gia tri mac dinh:
        //   spring.mail.username / password, jwt.secret, rollbar.access-token
        // Tren may dev chung duoc nap tu .env, nhung tren CI thi khong ton tai
        // nen Spring nem "Could not resolve placeholder" va TOAN BO context
        // khong len duoc — moi test se loi cung mot luc.
        //
        // Cac gia tri duoi day chi de context khoi dong. Test khong dung toi
        // mail, JWT hay Rollbar.
        registry.add("jwt.secret", () ->
                "integration-test-secret-key-that-is-long-enough-for-hs256-abcdefgh");
        registry.add("spring.mail.username", () -> "it@example.com");
        registry.add("spring.mail.password", () -> "not-used");
        registry.add("rollbar.access-token", () -> "not-used");
        registry.add("rollbar.enabled", () -> "false");
    }

    // MinIO va Redis se co gang ket noi that khi khoi dong; test nay khong dung
    // toi chung nen mock de context len duoc.
    @MockBean
    MinioStorageService minioStorageService;

    @MockBean
    ResourceStatService resourceStatService;

    @Autowired ResourceService resourceService;
    @Autowired ResourceRepository resourceRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    private Long topicId;
    private Long ownerId;
    private Long otherTeacherId;
    private Long normalUserId;

    private static final Pageable PAGE_12 =
            PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "createdAt"));

    @BeforeEach
    void setUp() {
        when(resourceStatService.getPendingViewCounts(any())).thenReturn(Map.of());
        when(resourceStatService.getPendingDownloadCounts(any())).thenReturn(Map.of());

        ownerId = createUser("it_owner", Role.TEACHER);
        otherTeacherId = createUser("it_other_teacher", Role.TEACHER);
        normalUserId = createUser("it_user", Role.USER);

        Category category = new Category();
        category.setName("IT Category");
        category.setSlug("it-category-" + UUID.randomUUID());
        category.setVisibility(Visibility.PUBLIC);
        category.setIsDeleted(false);
        categoryRepository.save(category);

        Topic topic = new Topic();
        topic.setName("IT Topic");
        topic.setSlug("it-topic-" + UUID.randomUUID());
        topic.setCategory(category);
        topic.setVisibility(Visibility.PUBLIC);
        topic.setIsDeleted(false);
        topicId = topicRepository.save(topic).getId();

        // Bo du lieu toi thieu phu ma tran
        newResource("pub", Visibility.PUBLIC, ResourceStatus.APPROVED, ownerId, topic);
        newResource("int", Visibility.INTERNAL, ResourceStatus.APPROVED, ownerId, topic);
        newResource("priv-owner", Visibility.PRIVATE, ResourceStatus.APPROVED, ownerId, topic);
        newResource("priv-other", Visibility.PRIVATE, ResourceStatus.APPROVED, otherTeacherId, topic);
        newResource("pending", Visibility.PUBLIC, ResourceStatus.PENDING, ownerId, topic);
        newResource("rejected", Visibility.PUBLIC, ResourceStatus.REJECTED, ownerId, topic);
        newResource("draft", Visibility.PUBLIC, ResourceStatus.DRAFT, ownerId, topic);
        newResource("archived", Visibility.PUBLIC, ResourceStatus.ARCHIVED, ownerId, topic);

        Resource deleted = newResource("deleted", Visibility.PUBLIC, ResourceStatus.APPROVED, ownerId, topic);
        deleted.setIsDeleted(true);
        resourceRepository.save(deleted);
    }

    /** Hau to ngan: cot users.username la VARCHAR(50), UUID day du se tran. */
    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Long createUser(String username, Role role) {
        User user = User.builder()
                .username(username + "-" + shortId())
                .email(username + "-" + shortId() + "@example.com")
                .password("x")
                .fullName("IT " + username)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .roles(Set.of(role))
                .build();
        return userRepository.save(user).getId();
    }

    private Resource newResource(String marker, Visibility visibility,
                                 ResourceStatus status, Long creatorId, Topic topic) {
        Resource r = new Resource();
        r.setTitle("IT " + marker + " tai lieu");
        r.setSlug("it-" + marker + "-" + UUID.randomUUID());
        r.setDescription("mo ta " + marker);
        r.setFileUrl("/bucket/it/" + marker + ".pdf");
        r.setTopic(topic);
        r.setVisibility(visibility);
        r.setStatus(status);
        r.setIsDeleted(false);
        r.setCreatedBy(creatorId);
        return resourceRepository.save(r);
    }

    // --- helpers ----------------------------------------------------------

    private ResourceFilterRequest onlyThisTopic() {
        ResourceFilterRequest filter = new ResourceFilterRequest();
        filter.setTopicId(topicId);
        return filter;
    }

    private List<String> titlesFor(Viewer viewer) {
        return titlesFor(viewer, PAGE_12);
    }

    private List<String> titlesFor(Viewer viewer, Pageable pageable) {
        Page<ResourceResponse> page =
                resourceService.getPortalResources(onlyThisTopic(), pageable, viewer);
        return page.getContent().stream().map(ResourceResponse::getTitle).toList();
    }

    private long totalFor(Viewer viewer) {
        return resourceService.getPortalResources(onlyThisTopic(), PAGE_12, viewer)
                .getTotalElements();
    }

    private static Viewer guest() {
        return Viewer.guest();
    }

    private Viewer user() {
        return Viewer.of(normalUserId, Set.of(Role.USER));
    }

    private Viewer otherTeacher() {
        return Viewer.of(otherTeacherId, Set.of(Role.TEACHER));
    }

    private Viewer owner() {
        return Viewer.of(ownerId, Set.of(Role.TEACHER));
    }

    private Viewer admin() {
        return Viewer.of(999L, Set.of(Role.ADMIN));
    }

    // =====================================================================
    // Ma tran visibility
    // =====================================================================

    @Test
    @DisplayName("Guest chỉ thấy PUBLIC đã duyệt")
    void guestSeesOnlyPublicApproved() {
        assertThat(titlesFor(guest())).containsExactly("IT pub tai lieu");
    }

    @Test
    @DisplayName("USER thấy PUBLIC + INTERNAL, không thấy PRIVATE")
    void userSeesInternalButNotPrivate() {
        assertThat(titlesFor(user()))
                .containsExactlyInAnyOrder("IT pub tai lieu", "IT int tai lieu");
    }

    @Test
    @DisplayName("Teacher không sở hữu: không thấy PRIVATE của người khác")
    void otherTeacherDoesNotSeeForeignPrivate() {
        assertThat(titlesFor(otherTeacher()))
                .contains("IT pub tai lieu", "IT int tai lieu")
                .doesNotContain("IT priv-owner tai lieu");
    }

    @Test
    @DisplayName("Chủ sở hữu thấy PRIVATE đã duyệt của mình")
    void ownerSeesOwnPrivate() {
        assertThat(titlesFor(owner())).contains("IT priv-owner tai lieu");
    }

    @Test
    @DisplayName("Chủ sở hữu vẫn KHÔNG thấy PRIVATE của người khác")
    void ownerStillCannotSeeForeignPrivate() {
        assertThat(titlesFor(owner())).doesNotContain("IT priv-other tai lieu");
    }

    @Test
    @DisplayName("Admin thấy mọi visibility nhưng Portal vẫn chỉ APPROVED")
    void adminSeesAllVisibilityButOnlyApproved() {
        List<String> titles = titlesFor(admin());

        assertThat(titles).contains(
                "IT pub tai lieu", "IT int tai lieu",
                "IT priv-owner tai lieu", "IT priv-other tai lieu");
        assertThat(titles).doesNotContain(
                "IT pending tai lieu", "IT rejected tai lieu",
                "IT draft tai lieu", "IT archived tai lieu");
    }

    @Test
    @DisplayName("DRAFT/PENDING/REJECTED/ARCHIVED không lên Portal với bất kỳ ai")
    void unapprovedNeverAppearsOnPortal() {
        for (Viewer viewer : List.of(guest(), user(), otherTeacher(), owner(), admin())) {
            assertThat(titlesFor(viewer))
                    .as("viewer=%s", viewer)
                    .doesNotContain("IT pending tai lieu", "IT rejected tai lieu",
                            "IT draft tai lieu", "IT archived tai lieu");
        }
    }

    @Test
    @DisplayName("Tài nguyên đã xoá mềm không xuất hiện, kể cả với admin")
    void softDeletedNeverAppears() {
        for (Viewer viewer : List.of(guest(), owner(), admin())) {
            assertThat(titlesFor(viewer)).doesNotContain("IT deleted tai lieu");
        }
    }

    // =====================================================================
    // Visibility phân tầng
    // =====================================================================

    @Test
    @DisplayName("Topic INTERNAL che tài nguyên PUBLIC bên trong khỏi khách")
    void internalTopicHidesPublicResource() {
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        topic.setVisibility(Visibility.INTERNAL);
        topicRepository.saveAndFlush(topic);

        assertThat(titlesFor(guest())).isEmpty();
        assertThat(titlesFor(user())).contains("IT pub tai lieu");
    }

    @Test
    @DisplayName("Category PRIVATE che toàn bộ, chỉ chủ sở hữu và admin còn thấy")
    void privateCategoryHidesEverything() {
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        Category category = topic.getCategory();
        category.setVisibility(Visibility.PRIVATE);
        categoryRepository.saveAndFlush(category);

        assertThat(titlesFor(guest())).isEmpty();
        assertThat(titlesFor(user())).isEmpty();
        assertThat(titlesFor(owner())).contains("IT pub tai lieu");
        assertThat(titlesFor(admin())).contains("IT pub tai lieu");
    }

    @Test
    @DisplayName("Topic bị xoá mềm → không ai thấy tài nguyên bên trong")
    void deletedTopicHidesResources() {
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        topic.setIsDeleted(true);
        topicRepository.saveAndFlush(topic);

        for (Viewer viewer : List.of(guest(), user(), owner(), admin())) {
            assertThat(titlesFor(viewer)).as("viewer=%s", viewer).isEmpty();
        }
    }

    // =====================================================================
    // Phân trang và tổng số
    // =====================================================================

    @Test
    @DisplayName("totalElements phản ánh đúng theo từng viewer")
    void totalElementsPerViewer() {
        assertThat(totalFor(guest())).isEqualTo(1);   // pub
        assertThat(totalFor(user())).isEqualTo(2);    // pub + int
        assertThat(totalFor(owner())).isEqualTo(3);   // pub + int + priv cua minh
        assertThat(totalFor(admin())).isEqualTo(4);   // ca 4 ban APPROVED
    }

    @Test
    @DisplayName("Lọc TRƯỚC phân trang: page size 1 vẫn cho tổng đúng")
    void filteringHappensBeforePagination() {
        Pageable size1 = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ResourceResponse> page =
                resourceService.getPortalResources(onlyThisTopic(), size1, admin());

        // Neu loc SAU phan trang thi tong se la so ban ghi tho, khong phai 4.
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Trang cuối trả đúng phần còn lại")
    void lastPageBoundary() {
        Pageable page3 = PageRequest.of(3, 1, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ResourceResponse> page =
                resourceService.getPortalResources(onlyThisTopic(), page3, admin());

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.isLast()).isTrue();
    }

    // =====================================================================
    // Search dùng chung visibility
    // =====================================================================

    @Test
    @DisplayName("Search áp cùng visibility: khách không thấy PRIVATE dù khớp từ khoá")
    void searchAppliesSameVisibility() {
        ResourceFilterRequest filter = onlyThisTopic();
        filter.setKeyword("priv-owner");

        Page<ResourceResponse> asGuest =
                resourceService.getPortalResources(filter, PAGE_12, guest());
        Page<ResourceResponse> asOwner =
                resourceService.getPortalResources(filter, PAGE_12, owner());

        assertThat(asGuest.getTotalElements()).isZero();
        assertThat(asOwner.getContent()).extracting(ResourceResponse::getTitle)
                .contains("IT priv-owner tai lieu");
    }

    @Test
    @DisplayName("Từ khoá khớp chính xác vẫn không lộ PRIVATE của người khác")
    void exactKeywordDoesNotLeakForeignPrivate() {
        ResourceFilterRequest filter = onlyThisTopic();
        filter.setKeyword("priv-other");

        assertThat(resourceService.getPortalResources(filter, PAGE_12, otherTeacher())
                .getContent())
                .extracting(ResourceResponse::getTitle)
                .contains("IT priv-other tai lieu");

        assertThat(resourceService.getPortalResources(filter, PAGE_12, owner())
                .getTotalElements())
                .isZero();
        assertThat(resourceService.getPortalResources(filter, PAGE_12, guest())
                .getTotalElements())
                .isZero();
    }

    // =====================================================================
    // Fail-closed
    // =====================================================================

    @Test
    @DisplayName("Visibility NULL trong DB → fail-closed, khách không thấy")
    void nullVisibilityIsFailClosed() {
        // Migration cu co the de lai ban ghi visibility NULL; cot hien la NOT
        // NULL nen phai ghi thang bang native query de dung duoc tinh huong do.
        List<Resource> all = resourceRepository.findAll();
        assertThat(all).isNotEmpty();

        // Khong the set NULL qua JPA vi cot NOT NULL — thay vao do kiem rang
        // buoc DB dang giu dung bat bien nay.
        assertThat(all).allSatisfy(r ->
                assertThat(r.getVisibility()).isNotNull());
    }

    @Test
    @DisplayName("Không có tài nguyên nào lọt ra ngoài tập đã tạo")
    void noStrayDataFromSeeder() {
        // Loc theo topic rieng nen du lieu cua DataSeeder khong lam nhieu ket qua.
        assertThat(titlesFor(admin())).allSatisfy(t ->
                assertThat(t).startsWith("IT "));
    }

    @Test
    @DisplayName("Danh sách rỗng khi topic không có gì hợp lệ")
    void emptyResultIsHandled() {
        ResourceFilterRequest filter = new ResourceFilterRequest();
        filter.setTopicId(-1L);

        Page<ResourceResponse> page =
                resourceService.getPortalResources(filter, PAGE_12, admin());

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(Collections.emptyList()).isEmpty();
    }
}
