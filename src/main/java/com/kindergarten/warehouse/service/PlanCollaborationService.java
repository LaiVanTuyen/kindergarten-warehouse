package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.LessonPlanResponses;
import com.kindergarten.warehouse.dto.response.PlanCollaborationResponses;
import com.kindergarten.warehouse.entity.LessonPlan;
import com.kindergarten.warehouse.entity.LessonPlanStatus;
import com.kindergarten.warehouse.entity.NotificationType;
import com.kindergarten.warehouse.entity.PlanComment;
import com.kindergarten.warehouse.entity.PlanReviewStatus;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.LessonPlanRepository;
import com.kindergarten.warehouse.repository.PlanCollaborationRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.Viewer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Phase 3 — "phối hợp trong trường": expert review, colleague comments, "đã sử dụng".
 * Access to a plan always goes through {@link LessonPlanService}'s read/own checks.
 */
@Service
@RequiredArgsConstructor
public class PlanCollaborationService {

    public enum Decision { APPROVE, REQUEST_CHANGES }

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LessonPlanService planService;
    private final LessonPlanRepository planRepository;
    private final PlanCollaborationRepository collabRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ review

    /** Owner sends a SHARED plan for "duyệt chuyên môn" (from NONE or CHANGES_REQUESTED). */
    @Transactional
    public void requestReview(Long planId, Viewer viewer) {
        planService.requireOwned(planId, viewer);
        if (planRepository.markPending(planId) == 0) {
            throw new AppException(ErrorCode.PLAN_REVIEW_STATE);
        }
    }

    @Transactional(readOnly = true)
    public Page<LessonPlanResponses.Summary> reviewQueue(Viewer admin, int page, int size) {
        return planRepository.findByReviewStatus(PlanReviewStatus.PENDING,
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)), Sort.by(Sort.Direction.ASC, "updatedAt")))
                .map(p -> LessonPlanService.toSummary(p, admin));
    }

    /**
     * ADMIN decision on the version they read. Refused (409) if the plan changed since, is no
     * longer pending, or another reviewer decided first. Owner is notified.
     */
    @Transactional
    public void decide(Long planId, Long version, Decision decision, String note, Viewer admin) {
        LessonPlan plan = planService.requireReadable(planId, admin);
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (decision == Decision.REQUEST_CHANGES && cleanNote == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        PlanReviewStatus target = decision == Decision.APPROVE ? PlanReviewStatus.APPROVED : PlanReviewStatus.CHANGES_REQUESTED;
        if (planRepository.decideReview(planId, version, target, cleanNote, admin.userId()) == 0) {
            throw new AppException(ErrorCode.PLAN_REVIEW_STATE);
        }
        boolean approved = target == PlanReviewStatus.APPROVED;
        notificationService.notify(plan.getOwner().getId(),
                approved ? NotificationType.PLAN_APPROVED : NotificationType.PLAN_CHANGES_REQUESTED,
                approved ? "Kế hoạch đã được duyệt chuyên môn" : "Kế hoạch cần chỉnh sửa trước khi duyệt",
                "\"" + plan.getTitle() + "\"" + (cleanNote == null ? "" : ": " + cleanNote),
                "/plans/" + planId);
    }

    // ---------------------------------------------------------------- comments

    @Transactional(readOnly = true)
    public PlanCollaborationResponses.Overview overview(Long planId, Viewer viewer) {
        LessonPlan plan = planService.requireReadable(planId, viewer);
        List<java.sql.Date> mine = collabRepository.lastUsedOn(planId, viewer.userId());
        LocalDate myLast = mine.isEmpty() || mine.get(0) == null ? null : mine.get(0).toLocalDate();
        List<PlanCollaborationResponses.Comment> comments = collabRepository.findCommentsByPlan(planId).stream()
                .map(c -> toComment(c, viewer, plan))
                .toList();
        return new PlanCollaborationResponses.Overview(collabRepository.countTeachersWhoUsed(planId), myLast != null,
                myLast, comments);
    }

    /**
     * Colleagues comment on plans open to them (shared/templates); the owner may reply on their own.
     * A draft is owner-only, so nobody else can reach it here.
     */
    @Transactional
    public PlanCollaborationResponses.Comment addComment(Long planId, String content, Viewer viewer) {
        LessonPlan plan = planService.requireReadable(planId, viewer);
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > 2000) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        User author = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        PlanComment saved = collabRepository.save(PlanComment.builder()
                .planId(planId).author(author).content(text).build());
        Long ownerId = plan.getOwner().getId();
        if (!Objects.equals(ownerId, viewer.userId())) {
            notificationService.notify(ownerId, NotificationType.PLAN_COMMENT,
                    "Có góp ý mới cho kế hoạch của bạn",
                    displayName(author) + " góp ý cho \"" + plan.getTitle() + "\": "
                            + (text.length() > 120 ? text.substring(0, 120) + "…" : text),
                    "/plans/" + planId);
        }
        return toComment(saved, viewer, plan);
    }

    /** Author or ADMIN may delete; the plan must still be readable by the caller. */
    @Transactional
    public void deleteComment(Long planId, Long commentId, Viewer viewer) {
        planService.requireReadable(planId, viewer);
        PlanComment comment = collabRepository.findById(commentId)
                .filter(c -> c.getPlanId().equals(planId))
                .orElseThrow(() -> new AppException(ErrorCode.PLAN_COMMENT_NOT_FOUND));
        if (!viewer.isAdmin() && !Objects.equals(comment.getAuthor().getId(), viewer.userId())) {
            throw new AppException(ErrorCode.PLAN_COMMENT_FORBIDDEN);
        }
        collabRepository.delete(comment);
    }

    // ------------------------------------------------------------------- usage

    /**
     * "Tôi đã dùng kế hoạch này": one row per teacher, counting repeats. Allowed on plans the
     * teacher can read; drafts of others are unreachable. Date defaults to today (Vietnam time)
     * and cannot be in the future.
     */
    @Transactional
    public PlanCollaborationResponses.Overview markUsed(Long planId, LocalDate usedOn, Viewer viewer) {
        LessonPlan plan = planService.requireReadable(planId, viewer);
        LocalDate today = LocalDate.now(VN);
        LocalDate date = usedOn == null ? today : usedOn;
        if (date.isAfter(today)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (plan.getStatus() != LessonPlanStatus.SHARED && !Boolean.TRUE.equals(plan.getTemplate())
                && !Objects.equals(plan.getOwner().getId(), viewer.userId())) {
            throw new AppException(ErrorCode.PLAN_NOT_FOUND);
        }
        collabRepository.recordUsage(planId, viewer.userId(), date);
        return overview(planId, viewer);
    }

    private PlanCollaborationResponses.Comment toComment(PlanComment c, Viewer viewer, LessonPlan plan) {
        boolean mine = Objects.equals(c.getAuthor().getId(), viewer.userId());
        return new PlanCollaborationResponses.Comment(c.getId(), c.getAuthor().getId(), displayName(c.getAuthor()),
                c.getContent(), c.getCreatedAt(), mine || viewer.isAdmin());
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : "Giáo viên";
    }
}
