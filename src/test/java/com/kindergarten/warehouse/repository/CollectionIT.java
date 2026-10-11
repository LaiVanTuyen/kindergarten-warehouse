package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.config.DataSeeder;
import com.kindergarten.warehouse.dto.request.CollectionRequests;
import com.kindergarten.warehouse.dto.response.CollectionResponses;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.CollectionVisibility;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** "Bộ học liệu" against real MySQL: ownership, sharing, per-viewer item visibility, copy, ordering. */
@Testcontainers
@SpringBootTest
class CollectionIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.46")
            .withDatabaseName("warehouse_collection_it")
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

    @Autowired CollectionService collectionService;
    @Autowired ResourceRepository resourceRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;
    @Autowired CollectionRepository collectionRepository;

    private Topic topic;
    private Viewer alice;   // teacher, collection owner
    private Viewer bob;     // another teacher
    private String songId;
    private String storyId;
    private String gameId;

    @BeforeEach
    void setUp() {
        when(resourceStatService.getPendingViewCounts(any())).thenReturn(Map.of());
        when(resourceStatService.getPendingDownloadCounts(any())).thenReturn(Map.of());

        alice = teacher("col_alice");
        bob = teacher("col_bob");
        Category category = new Category();
        category.setName("Col Category");
        category.setSlug("col-category-" + UUID.randomUUID());
        category.setVisibility(Visibility.PUBLIC);
        category.setIsDeleted(false);
        categoryRepository.save(category);
        topic = new Topic();
        topic.setName("Col Topic");
        topic.setSlug("col-topic-" + UUID.randomUUID());
        topic.setCategory(category);
        topic.setVisibility(Visibility.PUBLIC);
        topic.setIsDeleted(false);
        topicRepository.save(topic);

        songId = resource("Bài hát con vật", Visibility.PUBLIC, ResourceStatus.APPROVED, bob.userId()).getId();
        storyId = resource("Truyện con gà", Visibility.PUBLIC, ResourceStatus.APPROVED, bob.userId()).getId();
        gameId = resource("Trò chơi bắt chước", Visibility.PUBLIC, ResourceStatus.APPROVED, bob.userId()).getId();
    }

    @Test
    void ownerBuildsOrdersAndAnnotatesACollection() {
        var created = collectionService.create(upsert("Động vật quanh bé", CollectionVisibility.PRIVATE, null), alice);
        Long id = created.id();
        var song = collectionService.addItem(id, new CollectionRequests.AddItem(songId, "Mở đầu"), alice);
        var story = collectionService.addItem(id, new CollectionRequests.AddItem(storyId, null), alice);
        var game = collectionService.addItem(id, new CollectionRequests.AddItem(gameId, null), alice);

        expectError(() -> collectionService.addItem(id, new CollectionRequests.AddItem(songId, null), alice),
                ErrorCode.COLLECTION_ITEM_EXISTS);

        collectionService.reorder(id, new CollectionRequests.Reorder(List.of(game.id(), song.id(), story.id())), alice);
        collectionService.updateItem(id, story.id(), new CollectionRequests.UpdateItem("Kể bằng rối tay"), alice);

        var detail = collectionService.get(id, alice);
        assertThat(detail.items()).extracting(CollectionResponses.Item::resourceId)
                .containsExactly(gameId, songId, storyId);
        assertThat(detail.items()).extracting(CollectionResponses.Item::position).containsExactly(0, 1, 2);
        assertThat(detail.items().get(2).note()).isEqualTo("Kể bằng rối tay");
        assertThat(detail.collection().itemCount()).isEqualTo(3);

        // A reorder built from a stale list (missing the item another tab added) is rejected, not guessed.
        expectError(() -> collectionService.reorder(id,
                new CollectionRequests.Reorder(List.of(song.id(), game.id())), alice), ErrorCode.COLLECTION_STALE);

        collectionService.removeItem(id, song.id(), alice);
        assertThat(collectionService.get(id, alice).items()).extracting(CollectionResponses.Item::position)
                .containsExactly(0, 1);
    }

    @Test
    void privateIsOwnerOnlyAndSharedIsReadOnlyForColleagues() {
        Long id = collectionService.create(upsert("Riêng của Alice", CollectionVisibility.PRIVATE, null), alice).id();
        expectError(() -> collectionService.get(id, bob), ErrorCode.COLLECTION_NOT_FOUND);

        var current = collectionService.get(id, alice).collection();
        collectionService.update(id, new CollectionRequests.Upsert("Chia sẻ tổ", null, null, null,
                CollectionVisibility.SHARED, current.version()), alice);

        var seenByBob = collectionService.get(id, bob);
        assertThat(seenByBob.collection().isOwner()).isFalse();
        expectError(() -> collectionService.addItem(id, new CollectionRequests.AddItem(songId, null), bob),
                ErrorCode.COLLECTION_FORBIDDEN);
        expectError(() -> collectionService.delete(id, bob), ErrorCode.COLLECTION_FORBIDDEN);

        // Saving with the version from before the share is a stale edit.
        expectError(() -> collectionService.update(id, new CollectionRequests.Upsert("Ghi đè", null, null, null,
                CollectionVisibility.PRIVATE, current.version()), alice), ErrorCode.COLLECTION_STALE);
    }

    @Test
    void itemsAreFilteredPerViewerAndTakenDownResourcesBecomeUnavailable() {
        // Alice's own pending upload: she sees it, colleagues must not.
        String alicePending = resource("Bản nháp của Alice", Visibility.PUBLIC, ResourceStatus.PENDING,
                alice.userId()).getId();
        Long id = collectionService.create(upsert("Bộ chia sẻ", CollectionVisibility.SHARED, null), alice).id();
        collectionService.addItem(id, new CollectionRequests.AddItem(songId, null), alice);
        collectionService.addItem(id, new CollectionRequests.AddItem(alicePending, null), alice);

        // Song gets taken down after it was collected.
        Resource song = resourceRepository.findById(songId).orElseThrow();
        song.setStatus(ResourceStatus.REJECTED);
        resourceRepository.save(song);

        // Carol is neither the collection owner nor the song's uploader (Bob still sees his own rejected file).
        Viewer carol = teacher("col_carol");
        var forCarol = collectionService.get(id, carol);
        assertThat(forCarol.items()).allSatisfy(item -> {
            assertThat(item.available()).isFalse();
            assertThat(item.resource()).isNull();
        });
        var forAlice = collectionService.get(id, alice);
        assertThat(forAlice.items()).filteredOn(CollectionResponses.Item::available)
                .extracting(CollectionResponses.Item::resourceId).containsExactly(alicePending);
    }

    @Test
    void copyKeepsOrderAndNotesButOnlyWhatTheCopierMaySee() {
        String bobPrivate = resource("Tranh riêng của Bob", Visibility.PRIVATE, ResourceStatus.APPROVED,
                bob.userId()).getId();
        Long id = collectionService.create(upsert("Bộ của Bob", CollectionVisibility.SHARED, null), bob).id();
        collectionService.addItem(id, new CollectionRequests.AddItem(storyId, "Kể chuyện"), bob);
        collectionService.addItem(id, new CollectionRequests.AddItem(bobPrivate, null), bob);
        collectionService.addItem(id, new CollectionRequests.AddItem(songId, "Hát cuối"), bob);

        var copy = collectionService.copy(id, alice);

        assertThat(copy.isOwner()).isTrue();
        assertThat(copy.visibility()).isEqualTo(CollectionVisibility.PRIVATE);
        assertThat(copy.sourceCollectionId()).isEqualTo(id);
        var items = collectionService.get(copy.id(), alice).items();
        assertThat(items).extracting(CollectionResponses.Item::resourceId).containsExactly(storyId, songId);
        assertThat(items).extracting(CollectionResponses.Item::note).containsExactly("Kể chuyện", "Hát cuối");
        assertThat(items).extracting(CollectionResponses.Item::position).containsExactly(0, 1);
    }

    @Test
    void sharedSearchEscapesWildcardsAndMembershipFlagsContainingCollections() {
        Long shared = collectionService.create(upsert("Âm nhạc 100% vui", CollectionVisibility.SHARED, null), alice).id();
        collectionService.create(upsert("Âm nhạc 100 bài", CollectionVisibility.SHARED, null), alice);
        collectionService.create(upsert("Âm nhạc riêng 100%", CollectionVisibility.PRIVATE, null), alice);

        var found = collectionService.listShared(bob, "100%", null, 0, 20).getContent();
        assertThat(found).extracting(CollectionResponses.Summary::id).containsExactly(shared);

        collectionService.addItem(shared, new CollectionRequests.AddItem(gameId, null), alice);
        assertThat(collectionService.membershipFor(gameId, alice))
                .filteredOn(CollectionResponses.Membership::containsResource)
                .extracting(CollectionResponses.Membership::id).containsExactly(shared);
    }

    @Test
    void deletingACollectionRemovesItsItemsButNotTheResources() {
        Long id = collectionService.create(upsert("Tạm", CollectionVisibility.PRIVATE, null), alice).id();
        collectionService.addItem(id, new CollectionRequests.AddItem(songId, null), alice);
        collectionService.delete(id, alice);
        assertThat(collectionRepository.findById(id)).isEmpty();
        assertThat(resourceRepository.findById(songId)).isPresent();
    }

    // --- helpers ----------------------------------------------------------

    private static CollectionRequests.Upsert upsert(String title, CollectionVisibility visibility, Long version) {
        return new CollectionRequests.Upsert(title, null, null, null, visibility, version);
    }

    private static void expectError(ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(code);
    }

    private Viewer teacher(String name) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long id = userRepository.save(User.builder()
                .username(name + "-" + suffix)
                .email(name + "-" + suffix + "@example.com")
                .password("x")
                .fullName("Cô " + name)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .roles(Set.of(Role.TEACHER))
                .build()).getId();
        return Viewer.of(id, Set.of(Role.TEACHER));
    }

    private Resource resource(String title, Visibility visibility, ResourceStatus status, Long creatorId) {
        Resource r = new Resource();
        r.setTitle(title);
        r.setSlug("col-" + UUID.randomUUID());
        r.setFileUrl("/bucket/col/" + UUID.randomUUID() + ".pdf");
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
