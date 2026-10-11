package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.config.DataSeeder;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.NotificationType;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.scheduler.AuditLogCleanupScheduler;
import com.kindergarten.warehouse.scheduler.RedisSyncScheduler;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.service.MinioStorageService;
import com.kindergarten.warehouse.service.PortalDiscoveryService;
import com.kindergarten.warehouse.service.RedisLastActiveService;
import com.kindergarten.warehouse.service.ResourceService;
import com.kindergarten.warehouse.service.ResourceStatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Accent-insensitive search, tags, related resources, author stats, download
 * history and notifications against real MySQL (collation and the native
 * download upsert cannot be checked with mocks).
 *
 * <p>Fixtures are committed once per container; every assertion is scoped to
 * this class's own author so test order does not matter.</p>
 */
@Testcontainers
@SpringBootTest
class SearchAndDiscoveryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("warehouse_ft_it")
            .withUsername("it_user")
            .withPassword("it_pass")
            .withStartupTimeoutSeconds(300)
            .withConnectTimeoutSeconds(300);

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.admin.password", () -> "IntegrationTest#2026");
        registry.add("app.admin.email", () -> "it-admin@example.com");
        registry.add("jwt.secret", () ->
                "integration-test-secret-key-that-is-long-enough-for-hs256-abcdefgh");
        registry.add("spring.mail.username", () -> "it@example.com");
        registry.add("spring.mail.password", () -> "not-used");
        registry.add("rollbar.access-token", () -> "not-used");
        registry.add("rollbar.enabled", () -> "false");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "1");
    }

    @MockBean MinioStorageService minioStorageService;
    @MockBean ResourceStatService resourceStatService;
    @MockBean DataSeeder dataSeeder;
    @MockBean RedisSyncScheduler redisSyncScheduler;
    @MockBean AuditLogCleanupScheduler auditLogCleanupScheduler;
    @MockBean RedisLastActiveService redisLastActiveService;

    @Autowired ResourceService resourceService;
    @Autowired PortalDiscoveryService discoveryService;
    @Autowired ResourceRepository resourceRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ResourceReportRepository reportRepository;
    @Autowired com.kindergarten.warehouse.service.ResourceReportService reportService;

    private static boolean seeded;
    private static Long authorId;
    private static Long adminId;
    private static String adminUsername;
    private static String authorUsername;
    /** Owner of resources created by the regression tests, so the main author's stats stay fixed. */
    private static Long sideAuthorId;
    private static String sideAuthorUsername;
    private static Long topicId;
    private static String songSlug;
    private static String songId;
    private static String pendingId;

    @BeforeEach
    void setUp() {
        when(resourceStatService.getPendingViewCounts(any())).thenReturn(Map.of());
        when(resourceStatService.getPendingDownloadCounts(any())).thenReturn(Map.of());
        if (seeded) {
            return;
        }
        authorId = createUser("ft_teacher", Role.TEACHER);
        adminId = createUser("ft_admin", Role.ADMIN);
        adminUsername = userRepository.findById(adminId).orElseThrow().getUsername();
        authorUsername = userRepository.findById(authorId).orElseThrow().getUsername();
        sideAuthorId = createUser("ft_side_author", Role.TEACHER);
        sideAuthorUsername = userRepository.findById(sideAuthorId).orElseThrow().getUsername();

        Category category = new Category();
        category.setName("FT Category");
        category.setSlug("ft-category-" + UUID.randomUUID());
        category.setVisibility(Visibility.PUBLIC);
        category.setIsDeleted(false);
        categoryRepository.save(category);

        Topic music = newTopic("FT Âm nhạc", category);
        Topic art = newTopic("FT Tạo hình", category);
        topicId = music.getId();

        Resource song = newResource("Bài hát Cháu yêu bà", "Giai điệu vui nhộn cho trẻ 3-4 tuổi",
                Set.of("âm nhạc", "bà"), music, ResourceStatus.APPROVED, 50L);
        songSlug = song.getSlug();
        songId = song.getId();
        newResource("Bài hát Cả nhà thương nhau", "Bài hát về gia đình",
                Set.of("âm nhạc"), music, ResourceStatus.APPROVED, 10L);
        newResource("Tranh vẽ con gà", "Hướng dẫn vẽ con vật nuôi trong gia đình",
                Set.of("tạo hình"), art, ResourceStatus.APPROVED, 999L);
        pendingId = newResource("Bài hát chờ duyệt", "Chưa duyệt", Set.of("âm nhạc"), music,
                ResourceStatus.PENDING, 0L).getId();
        seeded = true;
    }

    @Test
    void searchIsAccentInsensitiveAndSearchesDescription() {
        assertThat(titles(keyword("bai hat"))).contains("Bài hát Cháu yêu bà", "Bài hát Cả nhà thương nhau")
                .doesNotContain("Bài hát chờ duyệt");
        assertThat(titles(keyword("gia dinh"))).contains("Bài hát Cả nhà thương nhau", "Tranh vẽ con gà");
    }

    @Test
    void keywordAlsoMatchesExactTagAndTagFilterWorks() {
        ResourceFilterRequest byTag = new ResourceFilterRequest();
        byTag.setTags(List.of("Tạo Hình"));
        assertThat(titles(byTag)).containsExactly("Tranh vẽ con gà");

        // "#bà" is not in any title/description, but normalizes to the exact tag "bà".
        assertThat(titles(keyword("#bà"))).contains("Bài hát Cháu yêu bà");
    }

    @Test
    void relatedPrefersSameTopicAndExcludesSelfAndPending() {
        List<ResourceResponse> related = resourceService.getRelatedResources(songSlug, 6, Viewer.guest());
        assertThat(related).extracting(ResourceResponse::getTitle)
                .startsWith("Bài hát Cả nhà thương nhau")
                .contains("Tranh vẽ con gà")
                .doesNotContain("Bài hát Cháu yêu bà", "Bài hát chờ duyệt");
    }

    @Test
    void authorProfileCountsOnlyPublicApprovedResources() {
        var author = discoveryService.getAuthor(authorId);
        assertThat(author.resourceCount()).isEqualTo(3);
        assertThat(author.totalViews()).isEqualTo(50 + 10 + 999);
        assertThat(discoveryService.getPopularTags(10))
                .anySatisfy(tag -> {
                    assertThat(tag.tag()).isEqualTo("âm nhạc");
                    assertThat(tag.count()).isGreaterThanOrEqualTo(2);
                });
    }

    @Test
    void downloadHistoryUpsertsAndHidesInvisibleResources() {
        Long reader = createUser("ft_reader", Role.USER);
        Viewer viewer = Viewer.of(reader, Set.of(Role.USER));
        discoveryService.recordDownload(reader, songId);
        discoveryService.recordDownload(reader, songId);
        discoveryService.recordDownload(reader, pendingId);

        var history = discoveryService.getDownloadHistory(viewer, PageRequest.of(0, 10));
        assertThat(history.getContent()).hasSize(1);
        assertThat(history.getContent().get(0).downloadCount()).isEqualTo(2);
        assertThat(history.getContent().get(0).resource().getId()).isEqualTo(songId);
    }

    @Test
    void approvingNotifiesUploader() {
        // Separate uploader so this approval does not change the main author's stats.
        Long uploader = createUser("ft_uploader", Role.TEACHER);
        Resource extra = newResource("Truyện chờ duyệt", "", Set.of(),
                topicRepository.findById(topicId).orElseThrow(), ResourceStatus.PENDING, 0L, uploader);

        resourceService.approveResource(extra.getId(), adminUsername);

        var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(uploader, PageRequest.of(0, 5));
        assertThat(notifications.getContent()).hasSize(1);
        assertThat(notifications.getContent().get(0).getType()).isEqualTo(NotificationType.RESOURCE_APPROVED);
        assertThat(notifications.getContent().get(0).getLink()).isEqualTo("/resources/" + extra.getSlug());
    }

    // --- Review 11/10: regression tests --------------------------------------

    @Test
    void takenDownResourceCannotBeRepublishedByOwnerTogglingVisibility() {
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        Long otherAuthor = createUser("ft_takedown_author", Role.TEACHER);
        String ownerName = userRepository.findById(otherAuthor).orElseThrow().getUsername();
        Resource bad = newResource("Tài liệu vi phạm", "", Set.of(), topic, ResourceStatus.APPROVED, 0L, otherAuthor);
        Long reporter = createUser("ft_reporter_td", Role.USER);

        var report = reportService.create(new com.kindergarten.warehouse.dto.request.ReportCreateRequest(
                bad.getId(), com.kindergarten.warehouse.entity.ReportReason.COPYRIGHT, null),
                Viewer.of(reporter, Set.of(Role.USER)));
        reportService.resolve(report.id(), new com.kindergarten.warehouse.dto.request.ReportResolveRequest(
                com.kindergarten.warehouse.dto.request.ReportResolveRequest.Action.RESOLVE, "Sao chép", true),
                adminUsername);

        // Owner tries to bring it back through the visibility endpoint.
        var toPublic = new com.kindergarten.warehouse.dto.request.VisibilityUpdateRequest();
        toPublic.setVisibility(Visibility.PUBLIC);
        resourceService.updateVisibility(bad.getId(), toPublic, ownerName);

        Resource reloaded = resourceRepository.findById(bad.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ResourceStatus.REJECTED);
        assertThat(reloaded.getRejectionReason()).contains("báo cáo #" + report.id());
        ResourceFilterRequest filter = new ResourceFilterRequest();
        filter.setCreatedBy(otherAuthor);
        assertThat(resourceService.getPortalResources(filter, PageRequest.of(0, 10), Viewer.guest())
                .getTotalElements()).isZero();
    }

    @Test
    void accentVariantTagsSaveAndUpdateWithoutDuplicateKey() {
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        // Before the fix Java kept both and MySQL (ai_ci PK) rejected the INSERT with 1062.
        Resource r = newResource("Tag có dấu", "", com.kindergarten.warehouse.util.TagUtils.forResource(
                List.of("bà, ba, Bà")), topic, ResourceStatus.APPROVED, 0L, sideAuthorId);
        assertThat(resourceRepository.findTagsByResourceIds(List.of(r.getId()))).hasSize(1);

        // Update path: replace "bà" by its unaccented spelling plus a new tag.
        var update = new com.kindergarten.warehouse.dto.request.ResourceUpdateRequest();
        update.setTags(List.of("ba", "đồ chơi", "do choi"));
        resourceService.updateResource(r.getId(), update, sideAuthorUsername);

        assertThat(resourceRepository.findTagsByResourceIds(List.of(r.getId())))
                .extracting(row -> (String) row[1])
                .containsExactlyInAnyOrder("ba", "đồ chơi");
    }

    @Test
    void concurrentDuplicateReportsLeaveExactlyOneOpen() throws Exception {
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        Resource r = newResource("Tài liệu báo cáo đồng thời", "", Set.of(), topic, ResourceStatus.APPROVED, 0L, sideAuthorId);
        Long reporter = createUser("ft_reporter_race", Role.USER);
        Viewer viewer = Viewer.of(reporter, Set.of(Role.USER));
        var request = new com.kindergarten.warehouse.dto.request.ReportCreateRequest(
                r.getId(), com.kindergarten.warehouse.entity.ReportReason.SPAM, null);

        List<Object> outcomes = runConcurrently(
                () -> reportService.create(request, viewer),
                () -> reportService.create(request, viewer));

        assertThat(outcomes).filteredOn(o -> o instanceof com.kindergarten.warehouse.dto.response.ReportResponse)
                .hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof com.kindergarten.warehouse.exception.AppException e
                && e.getErrorCode() == com.kindergarten.warehouse.exception.ErrorCode.REPORT_ALREADY_OPEN).hasSize(1);
        assertThat(reportRepository.existsByResourceIdAndReporterIdAndStatus(
                r.getId(), reporter, com.kindergarten.warehouse.entity.ReportStatus.OPEN)).isTrue();
        assertThat(reportRepository.findAll()).filteredOn(rep -> rep.getStatus()
                == com.kindergarten.warehouse.entity.ReportStatus.OPEN)
                .filteredOn(rep -> rep.getReporter().getId().equals(reporter)).hasSize(1);

        // Once closed, the same user may report again (closed rows don't hit the unique key).
        Long id = ((com.kindergarten.warehouse.dto.response.ReportResponse) outcomes.stream()
                .filter(o -> o instanceof com.kindergarten.warehouse.dto.response.ReportResponse)
                .findFirst().orElseThrow()).id();
        reportService.resolve(id, new com.kindergarten.warehouse.dto.request.ReportResolveRequest(
                com.kindergarten.warehouse.dto.request.ReportResolveRequest.Action.DISMISS, null, false), adminUsername);
        assertThat(reportService.create(request, viewer).status())
                .isEqualTo(com.kindergarten.warehouse.entity.ReportStatus.OPEN);
    }

    @Test
    void concurrentResolveAndDismissOnlyOneWinsAndNotifiesOnce() throws Exception {
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        Long owner = createUser("ft_race_owner", Role.TEACHER);
        Resource r = newResource("Tài liệu hai admin", "", Set.of(), topic, ResourceStatus.APPROVED, 0L, owner);
        Long reporter = createUser("ft_reporter_admins", Role.USER);
        var report = reportService.create(new com.kindergarten.warehouse.dto.request.ReportCreateRequest(
                r.getId(), com.kindergarten.warehouse.entity.ReportReason.COPYRIGHT, null),
                Viewer.of(reporter, Set.of(Role.USER)));
        Long secondAdmin = createUser("ft_admin2", Role.ADMIN);
        String secondAdminName = userRepository.findById(secondAdmin).orElseThrow().getUsername();

        List<Object> outcomes = runConcurrently(
                () -> reportService.resolve(report.id(), new com.kindergarten.warehouse.dto.request.ReportResolveRequest(
                        com.kindergarten.warehouse.dto.request.ReportResolveRequest.Action.RESOLVE, null, true), adminUsername),
                () -> reportService.resolve(report.id(), new com.kindergarten.warehouse.dto.request.ReportResolveRequest(
                        com.kindergarten.warehouse.dto.request.ReportResolveRequest.Action.DISMISS, null, false), secondAdminName));

        List<com.kindergarten.warehouse.dto.response.ReportResponse> winners = outcomes.stream()
                .filter(o -> o instanceof com.kindergarten.warehouse.dto.response.ReportResponse)
                .map(o -> (com.kindergarten.warehouse.dto.response.ReportResponse) o).toList();
        assertThat(winners).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof com.kindergarten.warehouse.exception.AppException e
                && e.getErrorCode() == com.kindergarten.warehouse.exception.ErrorCode.REPORT_ALREADY_RESOLVED).hasSize(1);

        // Stored state, resource state and notifications all match the single winner.
        var stored = reportRepository.findById(report.id()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(winners.get(0).status());
        boolean resolved = stored.getStatus() == com.kindergarten.warehouse.entity.ReportStatus.RESOLVED;
        assertThat(resourceRepository.findById(r.getId()).orElseThrow().getStatus())
                .isEqualTo(resolved ? ResourceStatus.REJECTED : ResourceStatus.APPROVED);
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(reporter, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void downloadHistoryFiltersBeforePagination() {
        Topic topic = topicRepository.findById(topicId).orElseThrow();
        Long reader = createUser("ft_reader_paging", Role.USER);
        Viewer viewer = Viewer.of(reader, Set.of(Role.USER));
        Resource visible = newResource("Còn hiển thị", "", Set.of(), topic, ResourceStatus.APPROVED, 0L, sideAuthorId);
        discoveryService.recordDownload(reader, visible.getId());
        for (int i = 0; i < 3; i++) {
            Resource hidden = newResource("Đã gỡ " + i, "", Set.of(), topic, ResourceStatus.REJECTED, 0L, sideAuthorId);
            discoveryService.recordDownload(reader, hidden.getId());
        }

        // Page size 2: before the fix page 1 could be all-hidden (empty) with total 4.
        var firstPage = discoveryService.getDownloadHistory(viewer, PageRequest.of(0, 2));
        assertThat(firstPage.getTotalElements()).isEqualTo(1);
        assertThat(firstPage.getTotalPages()).isEqualTo(1);
        assertThat(firstPage.getContent()).extracting(item -> item.resource().getId())
                .containsExactly(visible.getId());
    }

    /** Starts both tasks together; each outcome is the returned value or the thrown exception. */
    private static List<Object> runConcurrently(java.util.concurrent.Callable<?> a, java.util.concurrent.Callable<?> b)
            throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Object>> futures = new java.util.ArrayList<>();
            for (var task : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return (Object) task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> outcomes = new java.util.ArrayList<>();
            for (var f : futures) {
                outcomes.add(f.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    // --- helpers ----------------------------------------------------------

    private ResourceFilterRequest keyword(String keyword) {
        ResourceFilterRequest filter = new ResourceFilterRequest();
        filter.setKeyword(keyword);
        return filter;
    }

    private List<String> titles(ResourceFilterRequest filter) {
        return resourceService.getPortalResources(filter, PageRequest.of(0, 50), Viewer.guest())
                .getContent().stream()
                .filter(r -> r.getAuthorId() != null && r.getAuthorId().equals(authorId))
                .map(ResourceResponse::getTitle)
                .toList();
    }

    private Long createUser(String username, Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .username(username + "-" + suffix)
                .email(username + "-" + suffix + "@example.com")
                .password("x")
                .fullName("FT " + username)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .roles(Set.of(role))
                .build()).getId();
    }

    private Topic newTopic(String name, Category category) {
        Topic topic = new Topic();
        topic.setName(name);
        topic.setSlug("ft-topic-" + UUID.randomUUID());
        topic.setCategory(category);
        topic.setVisibility(Visibility.PUBLIC);
        topic.setIsDeleted(false);
        return topicRepository.save(topic);
    }

    private Resource newResource(String title, String description, Set<String> tags, Topic topic,
                                 ResourceStatus status, long views) {
        return newResource(title, description, tags, topic, status, views, authorId);
    }

    private Resource newResource(String title, String description, Set<String> tags, Topic topic,
                                 ResourceStatus status, long views, Long creatorId) {
        Resource r = new Resource();
        r.setTitle(title);
        r.setSlug("ft-" + UUID.randomUUID());
        r.setDescription(description);
        r.setFileUrl("/bucket/ft/" + UUID.randomUUID() + ".pdf");
        r.setTopic(topic);
        r.setVisibility(Visibility.PUBLIC);
        r.setStatus(status);
        r.setIsDeleted(false);
        r.setViewsCount(views);
        r.setDownloadCount(0L);
        r.setCreatedBy(creatorId);
        r.setTags(new LinkedHashSet<>(tags));
        return resourceRepository.save(r);
    }
}
