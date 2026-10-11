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
import org.springframework.data.domain.PageRequest;
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
    @Autowired com.kindergarten.warehouse.service.PlanCollaborationService collab;
    @Autowired NotificationRepository notificationRepository;
    @Autowired LessonPlanRepository planRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

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

    // --- Phase 3: review, comments, usage ------------------------------------

    /** A shared plan with real content, written as the full document the editor sends. */
    private LessonPlanRequest sharedDoc(String title, String objectives, String reflection, Long version) {
        return new LessonPlanRequest(title, null, null, 20, 25, objectives, "Giấy", null, reflection, null,
                LessonPlanStatus.SHARED, null, null, List.of(step("Khám phá", 20, List.of(songId))), version);
    }

    @Test
    void reviewFlowApprovesOnlyTheVersionReadAndContentEditsNeedReReview() {
        var draft = createPlan(request("Nháp chưa chia sẻ", null, null, null), alice);
        expectError(() -> collab.requestReview(draft.id(), alice), ErrorCode.PLAN_REVIEW_STATE);

        var plan = planService.create(sharedDoc("Màu sắc quanh bé", "Nhận biết màu đỏ", null, null), alice);
        expectError(() -> collab.requestReview(plan.id(), bob), ErrorCode.PLAN_FORBIDDEN);
        collab.requestReview(plan.id(), alice);
        var pending = planService.get(plan.id(), alice).plan();
        assertThat(pending.reviewStatus()).isEqualTo(com.kindergarten.warehouse.entity.PlanReviewStatus.PENDING);
        assertThat(pending.version()).isEqualTo(plan.version());   // requesting review does not bump the version
        assertThat(collab.reviewQueue(admin, 0, 50).getContent()).extracting(LessonPlanResponses.Summary::id)
                .contains(plan.id());

        // Owner edits content after the reviewer opened it: the reviewer's version is stale.
        Long reviewerSaw = pending.version();
        var edited = planService.update(plan.id(), sharedDoc("Màu sắc quanh bé", "Nhận biết màu đỏ, vàng", null, reviewerSaw), alice);
        expectError(() -> collab.decide(plan.id(), reviewerSaw, com.kindergarten.warehouse.service.PlanCollaborationService.Decision.APPROVE,
                null, admin), ErrorCode.PLAN_REVIEW_STATE);

        collab.decide(plan.id(), edited.version(), com.kindergarten.warehouse.service.PlanCollaborationService.Decision.APPROVE,
                "Rõ ràng, phù hợp lứa tuổi", admin);
        var approved = planService.get(plan.id(), alice);
        assertThat(approved.plan().reviewStatus()).isEqualTo(com.kindergarten.warehouse.entity.PlanReviewStatus.APPROVED);
        assertThat(approved.reviewNote()).isEqualTo("Rõ ràng, phù hợp lứa tuổi");
        assertThat(approved.plan().version()).isEqualTo(edited.version());   // owner's open editor still saves
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(alice.userId(), PageRequest.of(0, 1))
                .getContent().get(0).getType()).isEqualTo(com.kindergarten.warehouse.entity.NotificationType.PLAN_APPROVED);
        // A second decision on the same plan is refused.
        expectError(() -> collab.decide(plan.id(), edited.version(),
                com.kindergarten.warehouse.service.PlanCollaborationService.Decision.REQUEST_CHANGES, "x", admin),
                ErrorCode.PLAN_REVIEW_STATE);

        // Personal reflection is not reviewed content: approval stays.
        var reflected = planService.update(plan.id(), sharedDoc("Màu sắc quanh bé", "Nhận biết màu đỏ, vàng",
                "Trẻ hào hứng", edited.version()), alice);
        assertThat(reflected.reviewStatus()).isEqualTo(com.kindergarten.warehouse.entity.PlanReviewStatus.APPROVED);
        assertThat(planService.listLibrary(bob, null, null, true, 0, 50).getContent())
                .extracting(LessonPlanResponses.Summary::id).contains(plan.id());

        // Changing the content sends it back for review and out of the "approved" library.
        var changed = planService.update(plan.id(), sharedDoc("Màu sắc quanh bé", "Nhận biết 3 màu",
                "Trẻ hào hứng", reflected.version()), alice);
        assertThat(changed.reviewStatus()).isEqualTo(com.kindergarten.warehouse.entity.PlanReviewStatus.NONE);
        assertThat(planService.listLibrary(bob, null, null, true, 0, 50).getContent())
                .extracting(LessonPlanResponses.Summary::id).doesNotContain(plan.id());
    }

    @Test
    void ownerSaveRacingAnApprovalDoesNotOverwriteIt() throws Exception {
        var plan = planService.create(sharedDoc("Đua duyệt", "Mục tiêu", null, null), alice);
        collab.requestReview(plan.id(), alice);
        var tx = new org.springframework.transaction.support.TransactionTemplate(txManager);

        // The owner's transaction loads the plan (PENDING) ...
        tx.executeWithoutResult(status -> {
            var entity = planRepository.findById(plan.id()).orElseThrow();
            assertThat(entity.getReviewStatus()).isEqualTo(com.kindergarten.warehouse.entity.PlanReviewStatus.PENDING);
            // ... the reviewer approves from another thread meanwhile ...
            var reviewer = new Thread(() -> collab.decide(plan.id(), entity.getVersion(),
                    com.kindergarten.warehouse.service.PlanCollaborationService.Decision.APPROVE, null, admin));
            reviewer.start();
            try {
                reviewer.join(30_000);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            // ... then the owner's change (personal notes only) is flushed with the stale entity.
            entity.setReflection("Ghi nhận sau giờ học");
            planRepository.saveAndFlush(entity);
        });

        assertThat(planService.get(plan.id(), alice).plan().reviewStatus())
                .isEqualTo(com.kindergarten.warehouse.entity.PlanReviewStatus.APPROVED);
    }

    @Test
    void colleaguesCommentOnSharedPlansAndOwnerIsNotified() {
        var draft = createPlan(request("Nháp riêng", null, null, null), alice);
        expectError(() -> collab.addComment(draft.id(), "Góp ý", bob), ErrorCode.PLAN_NOT_FOUND);

        var plan = planService.create(sharedDoc("Kế hoạch chia sẻ", "Mục tiêu", null, null), alice);
        long before = notificationRepository.countByUserIdAndIsReadFalse(alice.userId());
        var comment = collab.addComment(plan.id(), "Nên thêm phần vận động cuối giờ", bob);
        assertThat(comment.canDelete()).isTrue();
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(alice.userId())).isEqualTo(before + 1);

        collab.addComment(plan.id(), "Cảm ơn chị, em sẽ thêm", alice);   // owner reply: no self-notification
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(alice.userId())).isEqualTo(before + 1);

        var forAlice = collab.overview(plan.id(), alice);
        assertThat(forAlice.comments()).extracting(c -> c.content())
                .containsExactly("Nên thêm phần vận động cuối giờ", "Cảm ơn chị, em sẽ thêm");
        assertThat(forAlice.comments().get(0).canDelete()).isFalse();
        expectError(() -> collab.deleteComment(plan.id(), comment.id(), alice), ErrorCode.PLAN_COMMENT_FORBIDDEN);
        collab.deleteComment(plan.id(), comment.id(), admin);
        assertThat(collab.overview(plan.id(), bob).comments()).hasSize(1);
    }

    @Test
    void usageIsCountedPerTeacherAndRejectsFutureDates() {
        var plan = planService.create(sharedDoc("Đã dùng", "Mục tiêu", null, null), alice);
        collab.markUsed(plan.id(), LocalDate.of(2026, 9, 1), bob);
        var overview = collab.markUsed(plan.id(), null, bob);
        assertThat(overview.teachersUsed()).isEqualTo(1);
        assertThat(overview.usedByMe()).isTrue();
        assertThat(collab.overview(plan.id(), alice).usedByMe()).isFalse();
        expectError(() -> collab.markUsed(plan.id(), LocalDate.now().plusDays(3), bob), ErrorCode.INVALID_REQUEST);
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
