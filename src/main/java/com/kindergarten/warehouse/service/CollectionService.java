package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.CollectionRequests;
import com.kindergarten.warehouse.dto.response.CollectionResponses;
import com.kindergarten.warehouse.dto.response.CollectionResponses.Item;
import com.kindergarten.warehouse.dto.response.CollectionResponses.Ref;
import com.kindergarten.warehouse.dto.response.CollectionResponses.Summary;
import com.kindergarten.warehouse.entity.AgeGroup;
import com.kindergarten.warehouse.entity.CollectionItem;
import com.kindergarten.warehouse.entity.CollectionVisibility;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceCollection;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.ResourceMapper;
import com.kindergarten.warehouse.repository.AgeGroupRepository;
import com.kindergarten.warehouse.repository.CollectionRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.ResourceAccessGuard;
import com.kindergarten.warehouse.security.Viewer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "Bộ học liệu" — ordered resource sets a teacher prepares for one lesson.
 *
 * <p>Access rules:</p>
 * <ul>
 *   <li>Only TEACHER/ADMIN use collections (enforced on the controller).</li>
 *   <li>PRIVATE: owner only. SHARED: any teacher/admin may open and copy. Only the owner edits.</li>
 *   <li>Every item is re-checked with {@link ResourceAccessGuard} for the CURRENT viewer on
 *       each read. Sharing or copying a collection never widens access to its resources.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    public static final int MAX_ITEMS = 50;
    public static final int MAX_COLLECTIONS_PER_OWNER = 200;

    private final CollectionRepository collectionRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final TopicRepository topicRepository;
    private final ResourceAccessGuard resourceAccessGuard;
    private final ResourceMapper resourceMapper;
    private final ResourceAvailabilityService availability;

    // ------------------------------------------------------------------ lists

    @Transactional(readOnly = true)
    public Page<Summary> listMine(Viewer viewer, int page, int size) {
        Page<ResourceCollection> result = collectionRepository.findByOwner(viewer.userId(), pageRequest(page, size));
        return toSummaries(result, viewer);
    }

    @Transactional(readOnly = true)
    public Page<Summary> listShared(Viewer viewer, String keyword, Long ageGroupId, int page, int size) {
        String pattern = keyword == null || keyword.isBlank() ? null
                : "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        Page<ResourceCollection> result = collectionRepository.searchByVisibility(
                CollectionVisibility.SHARED, pattern, ageGroupId, pageRequest(page, size));
        return toSummaries(result, viewer);
    }

    @Transactional(readOnly = true)
    public List<CollectionResponses.Membership> membershipFor(String resourceId, Viewer viewer) {
        List<ResourceCollection> mine = collectionRepository.findAllByOwnerOrderByUpdatedAtDesc(viewer.userId());
        if (mine.isEmpty()) {
            return List.of();
        }
        List<Long> ids = mine.stream().map(ResourceCollection::getId).toList();
        Map<Long, Long> counts = itemCounts(ids);
        Set<Long> containing = new HashSet<>(collectionRepository.findIdsContainingResource(ids, resourceId));
        return mine.stream()
                .map(c -> new CollectionResponses.Membership(c.getId(), c.getTitle(),
                        counts.getOrDefault(c.getId(), 0L), containing.contains(c.getId())))
                .toList();
    }

    // ----------------------------------------------------------------- detail

    @Transactional(readOnly = true)
    public CollectionResponses.Detail get(Long id, Viewer viewer) {
        ResourceCollection collection = requireReadable(id, viewer);
        List<CollectionItem> items = collection.getItems();

        Map<String, com.kindergarten.warehouse.dto.response.ResourceResponse> visible =
                availability.viewableResponses(items.stream().map(CollectionItem::getResourceId).toList(), viewer);
        List<Item> itemResponses = new ArrayList<>(items.size());
        for (CollectionItem item : items) {
            var resource = visible.get(item.getResourceId());
            itemResponses.add(new Item(item.getId(), item.getPosition(), item.getNote(), item.getResourceId(),
                    resource != null, resource));
        }
        return new CollectionResponses.Detail(toSummary(collection, items.size(), viewer), itemResponses);
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public Summary create(CollectionRequests.Upsert request, Viewer viewer) {
        if (collectionRepository.countByOwnerId(viewer.userId()) >= MAX_COLLECTIONS_PER_OWNER) {
            throw new AppException(ErrorCode.COLLECTION_LIMIT_REACHED);
        }
        User owner = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        ResourceCollection collection = ResourceCollection.builder().owner(owner).build();
        applyFields(collection, request);
        return toSummary(collectionRepository.save(collection), 0, viewer);
    }

    @Transactional
    public Summary update(Long id, CollectionRequests.Upsert request, Viewer viewer) {
        ResourceCollection collection = requireOwned(id, viewer);
        // Client sends the version it edited; a mismatch means another tab saved first.
        if (request.version() == null || !request.version().equals(collection.getVersion())) {
            throw new AppException(ErrorCode.COLLECTION_STALE);
        }
        applyFields(collection, request);
        collectionRepository.saveAndFlush(collection);
        return toSummary(collection, collection.getItems().size(), viewer);
    }

    @Transactional
    public void delete(Long id, Viewer viewer) {
        collectionRepository.delete(requireOwned(id, viewer));
    }

    @Transactional
    public Item addItem(Long id, CollectionRequests.AddItem request, Viewer viewer) {
        ResourceCollection collection = requireOwned(id, viewer);
        // The owner may only collect what they can see right now (404 otherwise, like the detail page).
        Resource resource = resourceAccessGuard.requireViewable(
                resourceRepository.findByIdWithDetails(request.resourceId()).orElse(null), viewer);
        List<CollectionItem> items = collection.getItems();
        if (items.stream().anyMatch(i -> i.getResourceId().equals(resource.getId()))) {
            throw new AppException(ErrorCode.COLLECTION_ITEM_EXISTS);
        }
        if (items.size() >= MAX_ITEMS) {
            throw new AppException(ErrorCode.COLLECTION_FULL);
        }
        CollectionItem item = CollectionItem.builder()
                .collection(collection)
                .resourceId(resource.getId())
                .position(items.stream().mapToInt(CollectionItem::getPosition).max().orElse(-1) + 1)
                .note(blankToNull(request.note()))
                .build();
        items.add(item);
        collection.touch();
        // Flushing bumps @Version; a concurrent add of the same resource hits
        // uk_collection_items_resource and surfaces as 409 from the global handler.
        collectionRepository.saveAndFlush(collection);
        return new Item(item.getId(), item.getPosition(), item.getNote(), resource.getId(), true,
                resourceMapper.toResponse(resource, false));
    }

    @Transactional
    public void updateItem(Long id, Long itemId, CollectionRequests.UpdateItem request, Viewer viewer) {
        ResourceCollection collection = requireOwned(id, viewer);
        CollectionItem item = findItem(collection, itemId);
        item.setNote(blankToNull(request.note()));
        collection.touch();
    }

    @Transactional
    public void removeItem(Long id, Long itemId, Viewer viewer) {
        ResourceCollection collection = requireOwned(id, viewer);
        CollectionItem item = findItem(collection, itemId);
        collection.getItems().remove(item);
        renumber(collection.getItems());
        collection.touch();
    }

    /**
     * The client sends the full list of item ids in the new order. If it does not
     * match the current set exactly (another tab added/removed an item), reject
     * instead of guessing — the client reloads and the teacher re-applies the move.
     */
    @Transactional
    public void reorder(Long id, CollectionRequests.Reorder request, Viewer viewer) {
        ResourceCollection collection = requireOwned(id, viewer);
        Map<Long, CollectionItem> byId = collection.getItems().stream()
                .collect(Collectors.toMap(CollectionItem::getId, Function.identity()));
        List<Long> requested = request.itemIds();
        if (requested.size() != byId.size() || !byId.keySet().equals(new HashSet<>(requested))) {
            throw new AppException(ErrorCode.COLLECTION_STALE);
        }
        List<CollectionItem> ordered = requested.stream().map(byId::get).toList();
        renumber(ordered);
        collection.getItems().sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
        collection.touch();
    }

    /**
     * Copies a readable collection into a new PRIVATE one owned by the viewer.
     * Only items the copier can see right now are copied; notes and order are kept.
     */
    @Transactional
    public Summary copy(Long id, Viewer viewer) {
        ResourceCollection source = requireReadable(id, viewer);
        if (collectionRepository.countByOwnerId(viewer.userId()) >= MAX_COLLECTIONS_PER_OWNER) {
            throw new AppException(ErrorCode.COLLECTION_LIMIT_REACHED);
        }
        User owner = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Map<String, Resource> visible = availability.viewable(
                source.getItems().stream().map(CollectionItem::getResourceId).toList(), viewer);

        String title = "Bản sao - " + source.getTitle();
        ResourceCollection copy = ResourceCollection.builder()
                .owner(owner)
                .title(title.length() > 200 ? title.substring(0, 200) : title)
                .description(source.getDescription())
                .ageGroup(source.getAgeGroup())
                .topic(source.getTopic())
                .visibility(CollectionVisibility.PRIVATE)
                .sourceCollectionId(source.getId())
                .build();
        int position = 0;
        for (CollectionItem item : source.getItems()) {
            if (!visible.containsKey(item.getResourceId())) {
                continue;
            }
            copy.getItems().add(CollectionItem.builder()
                    .collection(copy)
                    .resourceId(item.getResourceId())
                    .position(position++)
                    .note(item.getNote())
                    .build());
        }
        ResourceCollection saved = collectionRepository.save(copy);
        return toSummary(saved, saved.getItems().size(), viewer);
    }

    /**
     * Ordered resource ids of a collection the viewer may read, keeping only resources the
     * viewer can open. Used to seed a lesson plan from a collection.
     */
    @Transactional(readOnly = true)
    public List<String> viewableResourceIds(Long id, Viewer viewer) {
        ResourceCollection collection = requireReadable(id, viewer);
        List<String> ids = collection.getItems().stream().map(CollectionItem::getResourceId).toList();
        Set<String> visible = availability.viewable(ids, viewer).keySet();
        return ids.stream().filter(visible::contains).toList();
    }

    // ---------------------------------------------------------------- helpers

    private ResourceCollection requireReadable(Long id, Viewer viewer) {
        ResourceCollection collection = collectionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.COLLECTION_NOT_FOUND));
        boolean owner = Objects.equals(collection.getOwner().getId(), viewer.userId());
        boolean sharedToStaff = collection.getVisibility() == CollectionVisibility.SHARED && isStaff(viewer);
        if (!owner && !sharedToStaff && !viewer.isAdmin()) {
            // 404, not 403: don't reveal that a private collection with this id exists.
            throw new AppException(ErrorCode.COLLECTION_NOT_FOUND);
        }
        return collection;
    }

    private ResourceCollection requireOwned(Long id, Viewer viewer) {
        ResourceCollection collection = requireReadable(id, viewer);
        if (!Objects.equals(collection.getOwner().getId(), viewer.userId())) {
            throw new AppException(ErrorCode.COLLECTION_FORBIDDEN);
        }
        return collection;
    }

    private static boolean isStaff(Viewer viewer) {
        return viewer.roles() != null
                && (viewer.roles().contains(Role.TEACHER) || viewer.roles().contains(Role.ADMIN));
    }

    private void applyFields(ResourceCollection collection, CollectionRequests.Upsert request) {
        collection.setTitle(request.title().trim());
        collection.setDescription(blankToNull(request.description()));
        collection.setVisibility(request.visibility() == null ? CollectionVisibility.PRIVATE : request.visibility());
        AgeGroup ageGroup = request.ageGroupId() == null ? null : ageGroupRepository.findById(request.ageGroupId())
                .orElseThrow(() -> new AppException(ErrorCode.AGE_GROUP_NOT_FOUND));
        collection.setAgeGroup(ageGroup);
        Topic topic = request.topicId() == null ? null : topicRepository.findById(request.topicId())
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
        collection.setTopic(topic);
    }

    private static CollectionItem findItem(ResourceCollection collection, Long itemId) {
        return collection.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.COLLECTION_ITEM_NOT_FOUND));
    }

    private static void renumber(List<CollectionItem> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
        }
    }

    private Page<Summary> toSummaries(Page<ResourceCollection> page, Viewer viewer) {
        Map<Long, Long> counts = itemCounts(page.getContent().stream().map(ResourceCollection::getId).toList());
        return page.map(c -> toSummary(c, counts.getOrDefault(c.getId(), 0L), viewer));
    }

    private Map<Long, Long> itemCounts(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return collectionRepository.countItemsByCollectionIds(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private static Summary toSummary(ResourceCollection c, long itemCount, Viewer viewer) {
        User owner = c.getOwner();
        String ownerName = owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName() : "Giáo viên";
        return new Summary(
                c.getId(),
                c.getTitle(),
                c.getDescription(),
                c.getVisibility(),
                c.getAgeGroup() == null ? null : new Ref(c.getAgeGroup().getId(), c.getAgeGroup().getName()),
                c.getTopic() == null ? null : new Ref(c.getTopic().getId(), c.getTopic().getName()),
                owner.getId(),
                ownerName,
                Objects.equals(owner.getId(), viewer.userId()),
                itemCount,
                c.getSourceCollectionId(),
                c.getVersion(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
