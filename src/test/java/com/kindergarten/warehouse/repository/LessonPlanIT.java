package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.config.DataSeeder;
import com.kindergarten.warehouse.dto.request.CollectionRequests;
import com.kindergarten.warehouse.dto.request.LessonPlanRequest;
import com.kindergarten.warehouse.dto.response.LessonPlanResponses;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.CollectionVisibility;
import com.kindergarten.warehouse.entity.ContentSource;
import com.kindergarten.warehouse.entity.LessonPlanStatus;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.scheduler.AuditLogCleanupScheduler;
import com.kindergarten.warehouse.scheduler.RedisSyncScheduler;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.service.CollectionService;
import com.kindergarten.warehouse.service.LessonPlanService;
import com.kindergarten.warehouse.service.MinioStorageService;
import com.kindergarten.warehouse.service.RedisLastActiveService;
import com.kindergarten.warehouse.service.ResourceStatService;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** "Kế hoạch hoạt động" against real MySQL: structure, sharing, templates, copy, per-viewer resources, locking. */
@Testcontainers
@SpringBootTest
class LessonPlanIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("warehouse_plan_it")
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

    @Autowired LessonPlanService planService;
    @Autowired CollectionService collectionService;
    @Autowired ResourceRepository resourceRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    private Topic topic;
    private Viewer alice;
    private Viewer bob;
    private Viewer admin;
    private String songId;
    private String pictureId;

    @BeforeEach
    void setUp() {
        when(resourceStatService.getPendingViewCounts(any())).thenReturn(Map.of());
        when(resourceStatService.getPendingDownloadCounts(any())).thenReturn(Map.of());
        alice = user("plan_alice", Role.TEACHER);
        bob = user("plan_bob", Role.TEACHER);
        admin = user("plan_admin", Role.ADMIN);

        Category category = new Category();
        category.setName("Plan Category");
        category.setSlug("plan-category-" + UUID.randomUUID());
        category.setVisibility(Visibility.PUBLIC);
        category.setIsDeleted(false);
        categoryRepository.save(category);
        topic = new Topic();
        topic.setName("Màu sắc");
        topic.setSlug("plan-topic-" + UUID.randomUUID());
        topic.setCategory(category);
        topic.setVisibility(Visibility.PUBLIC);
        topic.setIsDeleted(false);
        topicRepository.save(topic);

        songId = resource("Bài hát màu sắc", Visibility.PUBLIC, ResourceStatus.APPROVED, bob.userId()).getId();
        pictureId = resource("Tranh cầu vồng", Visibility.PUBLIC, ResourceStatus.APPROVED, bob.userId()).getId();
    }

    @Test
    void blankPlanGetsDefaultStepsAndPlanFromCollectionSeedsExploreStep() {
        var blank = createPlan(request("Hoạt động màu sắc", null, null, null), alice);
        assertThat(planService.get(blank.id(), alice).steps()).extracting(LessonPlanResponses.Step::title)
                .containsExactly("Khởi động", "Khám phá", "Trò chơi", "Kết thúc");

        Long collectionId = collectionService.create(new CollectionRequests.Upsert(
                "Bộ màu sắc", null, null, null, CollectionVisibility.PRIVATE, null), alice).id();
        collectionService.addItem(collectionId, new CollectionRequests.AddItem(pictureId, null), alice);
        collectionService.addItem(collectionId, new CollectionRequests.AddItem(songId, null), alice);

        var fromCollection = createPlan(request("Từ bộ", null, collectionId, null), alice);
        var explore = planService.get(fromCollection.id(), alice).steps().get(1);
        assertThat(explore.title()).isEqualTo("Khám phá");
        assertThat(explore.resources()).extracting(LessonPlanResponses.StepResource::resourceId)
                .containsExactly(pictureId, songId);
    }

    @Test
    void updateReplacesStepsAndAStepOnlyEditStillBumpsTheVersion() {
        var created = createPlan(request("Kế hoạch", null, null, List.of(
                step("Khởi động", 5, List.of()), step("Khám phá", 10, List.of(songId)))), alice);
        Long v0 = created.version();

        // Same plan fields, only the steps change.
        var saved = updatePlan(created.id(), request("Kế hoạch", null, null, List.of(
                step("Khám phá", 12, List.of(pictureId, songId, songId)), step("Kết thúc", 3, List.of()))).withVersion(v0),
                alice);
        assertThat(saved.version()).isGreaterThan(v0);

        var detail = planService.get(created.id(), alice);
        assertThat(detail.steps()).extracting(LessonPlanResponses.Step::title).containsExactly("Khám phá", "Kết thúc");
        assertThat(detail.steps().get(0).resources()).extracting(LessonPlanResponses.StepResource::resourceId)
                .containsExactly(pictureId, songId);   // de-duplicated, order kept
        assertThat(detail.stepMinutesTotal()).isEqualTo(15);

        // A second tab still holding v0 must not overwrite.
        expectError(() -> updatePlan(created.id(),
                request("Tab cũ", null, null, List.of()).withVersion(v0), alice), ErrorCode.PLAN_STALE);
    }

    @Test
    void draftIsPrivateSharedIsReadOnlyAndOnlyAdminsMakeTemplates() {
        var draft = createPlan(request("Nháp", null, null, null), alice);
        expectError(() -> planService.get(draft.id(), bob), ErrorCode.PLAN_NOT_FOUND);

        var teacherTemplate = createPlan(request("Tự làm mẫu", null, null, null).asTemplate(), alice);
        assertThat(teacherTemplate.template()).isFalse();

        var shared = createPlan(request("Chia sẻ", LessonPlanStatus.SHARED, null, null), alice);
        assertThat(planService.get(shared.id(), bob).plan().isOwner()).isFalse();
        expectError(() -> planService.delete(shared.id(), bob), ErrorCode.PLAN_FORBIDDEN);

        var template = createPlan(request("Mẫu hoạt động tạo hình", null, null, null).asTemplate(), admin);
        assertThat(template.template()).isTrue();
        assertThat(template.status()).isEqualTo(LessonPlanStatus.DRAFT);
        assertThat(planService.get(template.id(), bob).plan().template()).isTrue();
        assertThat(planService.listTemplates(bob, "tạo hình", null, 0, 10).getContent())
                .extracting(LessonPlanResponses.Summary::id).containsExactly(template.id());
    }

    @Test
    void copyKeepsStructureDropsPersonalReflectionAndUnseeableResources() {
        String bobPrivate = resource("Tranh riêng", Visibility.PRIVATE, ResourceStatus.APPROVED, bob.userId()).getId();
        var source = planService.create(new LessonPlanRequest("Kế hoạch của Bob", null, topic.getId(), 20, 25,
                "Nhận biết màu đỏ, vàng", "Giấy, bút màu", "Trẻ nhút nhát làm cặp với bạn",
                "Trẻ thích phần trò chơi", LocalDate.of(2026, 10, 1), LessonPlanStatus.SHARED, null, null,
                List.of(step("Khám phá", 10, List.of(bobPrivate, songId))), null), bob);

        var copy = planService.copy(source.id(), alice);
        var detail = planService.get(copy.id(), alice);

        assertThat(copy.title()).isEqualTo("Bản sao - Kế hoạch của Bob");
        assertThat(copy.status()).isEqualTo(LessonPlanStatus.DRAFT);
        assertThat(copy.sourcePlanId()).isEqualTo(source.id());
        assertThat(detail.plan().objectives()).isEqualTo("Nhận biết màu đỏ, vàng");
        assertThat(detail.preparation()).isEqualTo("Giấy, bút màu");
        assertThat(detail.reflection()).isNull();
        assertThat(detail.plan().taughtOn()).isNull();
        assertThat(detail.steps().get(0).resources()).extracting(LessonPlanResponses.StepResource::resourceId)
                .containsExactly(songId);
    }

    @Test
    void resourcesAreCheckedPerViewerAndOnlyNewReferencesMustBeViewable() {
        var plan = createPlan(request("Kế hoạch", LessonPlanStatus.SHARED, null,
                List.of(step("Khám phá", 10, List.of(songId)))), alice);

        // Song gets taken down; Carol (neither owner of plan nor of song) can't see it.
        Resource song = resourceRepository.findById(songId).orElseThrow();
        song.setStatus(ResourceStatus.REJECTED);
        resourceRepository.save(song);
        Viewer carol = user("plan_carol", Role.TEACHER);
        var forCarol = planService.get(plan.id(), carol).steps().get(0).resources().get(0);
        assertThat(forCarol.available()).isFalse();
        assertThat(forCarol.resource()).isNull();

        // Alice can still save with the now-unavailable reference already in the plan…
        Long version = planService.get(plan.id(), alice).plan().version();
        var saved = updatePlan(plan.id(), request("Kế hoạch", LessonPlanStatus.SHARED, null,
                List.of(step("Khám phá", 10, List.of(songId)))).withVersion(version), alice);

        // …but cannot add a new reference she cannot open.
        String bobPrivate = resource("Tranh riêng", Visibility.PRIVATE, ResourceStatus.APPROVED, bob.userId()).getId();
        expectError(() -> updatePlan(plan.id(), request("Kế hoạch", LessonPlanStatus.SHARED, null,
                List.of(step("Khám phá", 10, List.of(songId, bobPrivate)))).withVersion(saved.version()), alice),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void sharedSearchMatchesObjectives() {
        var shared = planService.create(new LessonPlanRequest("Hoạt động 1", null, null, null, null,
                "Trẻ nhận biết 100% các màu cơ bản", null, null, null, null, LessonPlanStatus.SHARED, null, null,
                null, null), alice);
        planService.create(new LessonPlanRequest("Hoạt động 2", null, null, null, null,
                "Trẻ nhận biết 100 con vật", null, null, null, null, LessonPlanStatus.SHARED, null, null,
                null, null), alice);
        assertThat(planService.listShared(bob, "100%", null, 0, 10).getContent())
                .extracting(LessonPlanResponses.Summary::id).containsExactly(shared.id());
    }

    // --- helpers ----------------------------------------------------------

    @Test
    void sharedPlansKeepPersonalNotesPrivateAndMineSearchesObjectives() {
        var created = planService.create(new LessonPlanRequest("Buổi học màu sắc", null, null,
                25, 20, "nhận biết hình tròn", "Giấy", null, "Ghi chép cá nhân",
                LocalDate.of(2026, 10, 11), LessonPlanStatus.SHARED, false, null, null, null), alice);
        assertThat(planService.get(created.id(), alice).reflection()).isEqualTo("Ghi chép cá nhân");
        assertThat(planService.get(created.id(), bob).reflection()).isNull();
        assertThat(planService.get(created.id(), bob).plan().taughtOn()).isNull();
        assertThat(planService.listMine(alice, "hinh tron", 0, 10).getContent())
                .extracting(LessonPlanResponses.Summary::id).containsExactly(created.id());
        assertThat(planService.listMine(alice, "không có", 0, 10).getContent()).isEmpty();
    }

    /** Small builder so tests read like the plan they describe. */
    private record Req(String title, LessonPlanStatus status, Long collectionId,
                       List<LessonPlanRequest.Step> steps, Long version, Boolean template) {
        Req withVersion(Long v) {
            return new Req(title, status, collectionId, steps, v, template);
        }

        Req asTemplate() {
            return new Req(title, status, collectionId, steps, version, true);
        }
    }

    private static Req request(String title, LessonPlanStatus status, Long collectionId,
                               List<LessonPlanRequest.Step> steps) {
        return new Req(title, status, collectionId, steps, null, null);
    }

    private static LessonPlanRequest.Step step(String title, Integer minutes, List<String> resourceIds) {
        return new LessonPlanRequest.Step(title, minutes, null, ContentSource.TEACHER, resourceIds);
    }

    private static LessonPlanRequest toRequest(Req r) {
        return new LessonPlanRequest(r.title(), null, null, null, null, null, null, null, null, null,
                r.status(), r.template(), r.collectionId(), r.steps(), r.version());
    }

    private LessonPlanResponses.Summary createPlan(Req r, Viewer v) {
        return planService.create(toRequest(r), v);
    }

    private LessonPlanResponses.Summary updatePlan(Long id, Req r, Viewer v) {
        return planService.update(id, toRequest(r), v);
    }

    private static void expectError(ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(code);
    }

    private Viewer user(String name, Role role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long id = userRepository.save(User.builder()
                .username(name + "-" + suffix)
                .email(name + "-" + suffix + "@example.com")
                .password("x")
                .fullName("Cô " + name)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .roles(Set.of(role))
                .build()).getId();
        return Viewer.of(id, Set.of(role));
    }

    private Resource resource(String title, Visibility visibility, ResourceStatus status, Long creatorId) {
        Resource r = new Resource();
        r.setTitle(title);
        r.setSlug("plan-" + UUID.randomUUID());
        r.setFileUrl("/bucket/plan/" + UUID.randomUUID() + ".pdf");
        r.setTopic(topic);
        r.setVisibility(visibility);
        r.setStatus(status);
        r.setIsDeleted(false);
        r.setViewsCount(0L);
        r.setDownloadCount(0L);
        r.setCreatedBy(creatorId);
        return resourceRepository.save(r);
    }
}
