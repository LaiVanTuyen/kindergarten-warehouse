package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.LessonPlanRequest;
import com.kindergarten.warehouse.dto.response.CollectionResponses.Ref;
import com.kindergarten.warehouse.dto.response.LessonPlanResponses;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.ContentSource;
import com.kindergarten.warehouse.entity.LessonPlan;
import com.kindergarten.warehouse.entity.LessonPlanStatus;
import com.kindergarten.warehouse.entity.LessonPlanStep;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.AgeGroupRepository;
import com.kindergarten.warehouse.repository.LessonPlanRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.ResourceAccessGuard;
import com.kindergarten.warehouse.security.Viewer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * "Kế hoạch hoạt động".
 *
 * <p>Access mirrors collections: DRAFT is owner-only, SHARED and templates are readable by
 * any teacher/admin, only the owner edits. Resource references are re-checked per viewer
 * on every read; copying keeps only what the copier may open.</p>
 */
@Service
@RequiredArgsConstructor
public class LessonPlanService {

    public static final int MAX_PLANS_PER_OWNER = 500;

    /** Default progression for a blank plan: Khởi động → Khám phá → Trò chơi → Kết thúc. */
    static final List<String> DEFAULT_STEPS = List.of("Khởi động", "Khám phá", "Trò chơi", "Kết thúc");

    private final LessonPlanRepository planRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final TopicRepository topicRepository;
    private final ResourceAccessGuard resourceAccessGuard;
    private final ResourceAvailabilityService availability;
    private final CollectionService collectionService;

    // ------------------------------------------------------------------ lists

    @Transactional(readOnly = true)
    public Page<LessonPlanResponses.Summary> listMine(Viewer viewer, int page, int size) {
        return listMine(viewer, null, page, size);
    }

    @Transactional(readOnly = true)
    public Page<LessonPlanResponses.Summary> listMine(Viewer viewer, String keyword, int page, int size) {
        return planRepository.findByOwner(viewer.userId(), likePattern(keyword), pageRequest(page, size))
                .map(p -> toSummary(p, viewer));
    }

    @Transactional(readOnly = true)
    public Page<LessonPlanResponses.Summary> listShared(Viewer viewer, String keyword, Long ageGroupId,
            int page, int size) {
        return planRepository.search(false, LessonPlanStatus.SHARED, likePattern(keyword), ageGroupId, false,
                pageRequest(page, size)).map(p -> toSummary(p, viewer));
    }

    /** "Thư viện kế hoạch của trường": shared plans, optionally only those approved by a reviewer. */
    @Transactional(readOnly = true)
    public Page<LessonPlanResponses.Summary> listLibrary(Viewer viewer, String keyword, Long ageGroupId,
            boolean approvedOnly, int page, int size) {
        return planRepository.search(false, LessonPlanStatus.SHARED, likePattern(keyword), ageGroupId, approvedOnly,
                pageRequest(page, size)).map(p -> toSummary(p, viewer));
    }

    @Transactional(readOnly = true)
    public Page<LessonPlanResponses.Summary> listTemplates(Viewer viewer, String keyword, Long ageGroupId,
            int page, int size) {
        return planRepository.search(true, null, likePattern(keyword), ageGroupId, false, pageRequest(page, size))
                .map(p -> toSummary(p, viewer));
    }

    // ----------------------------------------------------------------- detail

    @Transactional(readOnly = true)
    public LessonPlanResponses.Detail get(Long id, Viewer viewer) {
        LessonPlan plan = requireReadable(id, viewer);
        Set<String> allIds = new HashSet<>();
        plan.getSteps().forEach(step -> allIds.addAll(nonNull(step.getResourceIds())));
        Map<String, ResourceResponse> visible = availability.viewableResponses(allIds, viewer);

        List<LessonPlanResponses.Step> steps = new ArrayList<>();
        int total = 0;
        for (LessonPlanStep step : plan.getSteps()) {
            List<LessonPlanResponses.StepResource> resources = nonNull(step.getResourceIds()).stream()
                    .map(rid -> new LessonPlanResponses.StepResource(rid, visible.containsKey(rid), visible.get(rid)))
                    .toList();
            steps.add(new LessonPlanResponses.Step(step.getId(), step.getPosition(), step.getTitle(),
                    step.getDurationMinutes(), step.getDescription(), step.getSource(), resources));
            total += step.getDurationMinutes() == null ? 0 : step.getDurationMinutes();
        }
        return new LessonPlanResponses.Detail(toSummary(plan, viewer), plan.getClassSize(), plan.getPreparation(),
                plan.getAdaptations(), Objects.equals(plan.getOwner().getId(), viewer.userId()) || viewer.isAdmin()
                        ? plan.getReflection() : null, plan.getCollectionId(), plan.getReviewNote(), total, steps);
    }

    // ------------------------------------------------------------------ write

    /**
     * Blank plans get the default four steps. With {@code collectionId} and no steps,
     * the collection's resources (those the teacher can open) go into "Khám phá".
     */
    @Transactional
    public LessonPlanResponses.Summary create(LessonPlanRequest request, Viewer viewer) {
        if (planRepository.countByOwnerId(viewer.userId()) >= MAX_PLANS_PER_OWNER) {
            throw new AppException(ErrorCode.PLAN_LIMIT_REACHED);
        }
        User owner = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        LessonPlan plan = LessonPlan.builder().owner(owner).build();
        applyFields(plan, request, viewer);

        List<LessonPlanRequest.Step> steps = request.steps();
        if (steps == null || steps.isEmpty()) {
            List<String> fromCollection = request.collectionId() == null ? List.of()
                    : collectionService.viewableResourceIds(request.collectionId(), viewer);
            steps = DEFAULT_STEPS.stream()
                    .map(title -> new LessonPlanRequest.Step(title, null, null, ContentSource.TEACHER,
                            title.equals("Khám phá") ? limit(fromCollection, 10) : List.of()))
                    .toList();
        }
        replaceSteps(plan, steps, Set.of(), viewer);
        return toSummary(planRepository.save(plan), viewer);
    }

    @Transactional
    public LessonPlanResponses.Summary update(Long id, LessonPlanRequest request, Viewer viewer) {
        LessonPlan plan = requireOwned(id, viewer);
        if (request.version() == null || !request.version().equals(plan.getVersion())) {
            throw new AppException(ErrorCode.PLAN_STALE);
        }
        // References already in the plan are kept even if they became unavailable (the
        // teacher sees "không còn khả dụng" and decides); only NEW ones must be viewable.
        Set<String> existing = new HashSet<>();
        plan.getSteps().forEach(step -> existing.addAll(nonNull(step.getResourceIds())));
        String contentBefore = reviewedContent(plan);
        applyFields(plan, request, viewer);
        replaceSteps(plan, request.steps() == null ? List.of() : request.steps(), existing, viewer);
        // An approval covers the content the reviewer read. Changing that content (not the
        // personal reflection/date, not AI-review marks) sends the plan back for review;
        // un-sharing withdraws it from review altogether.
        boolean resetReview = plan.getReviewStatus() != com.kindergarten.warehouse.entity.PlanReviewStatus.NONE
                && (plan.getStatus() != LessonPlanStatus.SHARED
                        || (plan.getReviewStatus() == com.kindergarten.warehouse.entity.PlanReviewStatus.APPROVED
                                && !contentBefore.equals(reviewedContent(plan))));
        // Step edits alone don't dirty the plan row, so @Version wouldn't move and a second
        // tab saving the same version would silently win. Touching the row forces the bump.
        plan.setUpdatedAt(java.time.LocalDateTime.now());
        planRepository.saveAndFlush(plan);
        if (resetReview) {
            planRepository.resetReview(plan.getId());
            // Mirror in memory for the response (columns are not written by the entity).
            plan.setReviewStatus(com.kindergarten.warehouse.entity.PlanReviewStatus.NONE);
            plan.setReviewNote(null);
            plan.setReviewedBy(null);
            plan.setReviewedAt(null);
        }
        return toSummary(plan, viewer);
    }

    @Transactional
    public void delete(Long id, Viewer viewer) {
        planRepository.delete(requireOwned(id, viewer));
    }

    /**
     * New DRAFT owned by the viewer. Steps, objectives and preparation are copied; the
     * source's post-lesson reflection and date are personal and are not. Only resources
     * the copier can open are carried over.
     */
    @Transactional
    public LessonPlanResponses.Summary copy(Long id, Viewer viewer) {
        LessonPlan source = requireReadable(id, viewer);
        if (planRepository.countByOwnerId(viewer.userId()) >= MAX_PLANS_PER_OWNER) {
            throw new AppException(ErrorCode.PLAN_LIMIT_REACHED);
        }
        User owner = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Set<String> allIds = new HashSet<>();
        source.getSteps().forEach(step -> allIds.addAll(nonNull(step.getResourceIds())));
        Set<String> visible = availability.viewable(allIds, viewer).keySet();

        String title = Boolean.TRUE.equals(source.getTemplate()) ? source.getTitle()
                : "Bản sao - " + source.getTitle();
        LessonPlan copy = LessonPlan.builder()
                .owner(owner)
                .title(title.length() > 200 ? title.substring(0, 200) : title)
                .ageGroup(source.getAgeGroup())
                .topic(source.getTopic())
                .durationMinutes(source.getDurationMinutes())
                .classSize(source.getClassSize())
                .objectives(source.getObjectives())
                .preparation(source.getPreparation())
                .adaptations(source.getAdaptations())
                .status(LessonPlanStatus.DRAFT)
                .template(false)
                .sourcePlanId(source.getId())
                .build();
        int position = 0;
        for (LessonPlanStep step : source.getSteps()) {
            copy.getSteps().add(LessonPlanStep.builder()
                    .plan(copy)
                    .position(position++)
                    .title(step.getTitle())
                    .durationMinutes(step.getDurationMinutes())
                    .description(step.getDescription())
                    .source(step.getSource())
                    .resourceIds(new ArrayList<>(nonNull(step.getResourceIds()).stream()
                            .filter(visible::contains).toList()))
                    .build());
        }
        return toSummary(planRepository.save(copy), viewer);
    }

    // ---------------------------------------------------------------- helpers

    LessonPlan requireReadable(Long id, Viewer viewer) {
        LessonPlan plan = planRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_NOT_FOUND));
        boolean owner = Objects.equals(plan.getOwner().getId(), viewer.userId());
        boolean openToStaff = (plan.getStatus() == LessonPlanStatus.SHARED || Boolean.TRUE.equals(plan.getTemplate()))
                && isStaff(viewer);
        if (!owner && !openToStaff && !viewer.isAdmin()) {
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }
        return plan;
    }

    LessonPlan requireOwned(Long id, Viewer viewer) {
        LessonPlan plan = requireReadable(id, viewer);
        if (!Objects.equals(plan.getOwner().getId(), viewer.userId())) {
            throw new AppException(ErrorCode.PLAN_FORBIDDEN);
        }
        return plan;
    }

    private static boolean isStaff(Viewer viewer) {
        return viewer.roles() != null
                && (viewer.roles().contains(Role.TEACHER) || viewer.roles().contains(Role.ADMIN));
    }

    private void applyFields(LessonPlan plan, LessonPlanRequest request, Viewer viewer) {
        plan.setTitle(request.title().trim());
        plan.setAgeGroup(request.ageGroupId() == null ? null : ageGroupRepository.findById(request.ageGroupId())
                .orElseThrow(() -> new AppException(ErrorCode.AGE_GROUP_NOT_FOUND)));
        plan.setTopic(request.topicId() == null ? null : topicRepository.findById(request.topicId())
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND)));
        plan.setDurationMinutes(request.durationMinutes());
        plan.setClassSize(request.classSize());
        plan.setObjectives(blankToNull(request.objectives()));
        plan.setPreparation(blankToNull(request.preparation()));
        plan.setAdaptations(blankToNull(request.adaptations()));
        plan.setReflection(blankToNull(request.reflection()));
        plan.setTaughtOn(request.taughtOn());
        plan.setStatus(request.status() == null ? LessonPlanStatus.DRAFT : request.status());
        if (viewer.isAdmin() && request.template() != null) {
            plan.setTemplate(request.template());
        }
        if (request.collectionId() != null && !request.collectionId().equals(plan.getCollectionId())) {
            // Linking a collection requires being able to read it (404 otherwise).
            collectionService.viewableResourceIds(request.collectionId(), viewer);
        }
        plan.setCollectionId(request.collectionId());
    }

    private void replaceSteps(LessonPlan plan, List<LessonPlanRequest.Step> steps, Set<String> alreadyReferenced,
            Viewer viewer) {
        // Validate every newly referenced resource BEFORE mutating, so a 404 leaves the plan untouched.
        Set<String> checked = new HashSet<>(alreadyReferenced);
        for (LessonPlanRequest.Step step : steps) {
            for (String rid : cleanIds(step.resourceIds())) {
                if (checked.add(rid)) {
                    resourceAccessGuard.requireViewable(resourceRepository.findByIdWithDetails(rid).orElse(null), viewer);
                }
            }
        }
        plan.getSteps().clear();
        int position = 0;
        for (LessonPlanRequest.Step step : steps) {
            plan.getSteps().add(LessonPlanStep.builder()
                    .plan(plan)
                    .position(position++)
                    .title(step.title().trim())
                    .durationMinutes(step.durationMinutes())
                    .description(blankToNull(step.description()))
                    .source(step.source() == null ? ContentSource.TEACHER : step.source())
                    .resourceIds(new ArrayList<>(cleanIds(step.resourceIds())))
                    .build());
        }
    }

    /** Trimmed, non-blank, de-duplicated, order kept, at most 10. */
    private static List<String> cleanIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                clean.add(id.trim());
            }
        }
        return limit(new ArrayList<>(clean), 10);
    }

    private static List<String> limit(List<String> ids, int max) {
        return ids.size() <= max ? ids : ids.subList(0, max);
    }

    /** {@code @OrderColumn} lists contain null for gaps left by hard-deleted resources. */
    private static List<String> nonNull(List<String> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).toList();
    }

    /** What a reviewer approves: shared content only (no reflection, date, sharing flags, or step source). */
    static String reviewedContent(LessonPlan p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getTitle()).append('\u0001')
                .append(p.getAgeGroup() == null ? "" : p.getAgeGroup().getId()).append('\u0001')
                .append(p.getTopic() == null ? "" : p.getTopic().getId()).append('\u0001')
                .append(p.getDurationMinutes()).append('\u0001').append(p.getClassSize()).append('\u0001')
                .append(p.getObjectives()).append('\u0001').append(p.getPreparation()).append('\u0001')
                .append(p.getAdaptations());
        for (LessonPlanStep s : p.getSteps()) {
            sb.append('\u0002').append(s.getTitle()).append('\u0001').append(s.getDurationMinutes())
                    .append('\u0001').append(s.getDescription()).append('\u0001').append(nonNull(s.getResourceIds()));
        }
        return sb.toString();
    }

    static LessonPlanResponses.Summary toSummary(LessonPlan p, Viewer viewer) {
        User owner = p.getOwner();
        String ownerName = owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName() : "Giáo viên";
        return new LessonPlanResponses.Summary(
                p.getId(),
                p.getTitle(),
                p.getAgeGroup() == null ? null : new Ref(p.getAgeGroup().getId(), p.getAgeGroup().getName()),
                p.getTopic() == null ? null : new Ref(p.getTopic().getId(), p.getTopic().getName()),
                p.getDurationMinutes(),
                p.getObjectives(),
                p.getStatus(),
                Boolean.TRUE.equals(p.getTemplate()),
                p.getReviewStatus(),
                owner.getId(),
                ownerName,
                Objects.equals(owner.getId(), viewer.userId()),
                p.getSteps().size(),
                Objects.equals(owner.getId(), viewer.userId()) || viewer.isAdmin() ? p.getTaughtOn() : null,
                p.getSourcePlanId(),
                p.getVersion(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
