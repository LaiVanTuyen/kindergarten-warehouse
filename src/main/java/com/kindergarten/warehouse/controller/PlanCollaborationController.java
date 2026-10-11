package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.LessonPlanResponses;
import com.kindergarten.warehouse.dto.response.PlanCollaborationResponses;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.LessonPlanService;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.PlanCollaborationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Phase 3: school plan library, expert review, comments, "đã sử dụng". */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TEACHER', 'ADMIN')")
public class PlanCollaborationController {

    public record CommentRequest(@NotBlank @Size(max = 2000) String content) {
    }

    public record UsageRequest(LocalDate usedOn) {
    }

    public record DecisionRequest(@NotNull Long version, @NotNull PlanCollaborationService.Decision decision,
            @Size(max = 1000) String note) {
    }

    private final PlanCollaborationService collab;
    private final LessonPlanService planService;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    /** "Thư viện kế hoạch của trường": shared plans, optionally only approved ones. */
    @GetMapping("/api/v1/plans/library")
    public ResponseEntity<ApiResponse<Page<LessonPlanResponses.Summary>>> library(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(defaultValue = "false") boolean approvedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return ok(planService.listLibrary(viewer(authentication), keyword, ageGroupId, approvedOnly, page, size));
    }

    @GetMapping("/api/v1/plans/{id}/collaboration")
    public ResponseEntity<ApiResponse<PlanCollaborationResponses.Overview>> overview(
            @PathVariable Long id, Authentication authentication) {
        return ok(collab.overview(id, viewer(authentication)));
    }

    @PostMapping("/api/v1/plans/{id}/comments")
    public ResponseEntity<ApiResponse<PlanCollaborationResponses.Comment>> comment(
            @PathVariable Long id, @Valid @RequestBody CommentRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                collab.addComment(id, request.content(), viewer(authentication)), message()));
    }

    @DeleteMapping("/api/v1/plans/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long id, @PathVariable Long commentId,
            Authentication authentication) {
        collab.deleteComment(id, commentId, viewer(authentication));
        return ok(null);
    }

    @PostMapping("/api/v1/plans/{id}/usage")
    public ResponseEntity<ApiResponse<PlanCollaborationResponses.Overview>> markUsed(
            @PathVariable Long id, @RequestBody(required = false) UsageRequest request, Authentication authentication) {
        return ok(collab.markUsed(id, request == null ? null : request.usedOn(), viewer(authentication)));
    }

    @PostMapping("/api/v1/plans/{id}/review-request")
    public ResponseEntity<ApiResponse<Void>> requestReview(@PathVariable Long id, Authentication authentication) {
        collab.requestReview(id, viewer(authentication));
        return ok(null);
    }

    @GetMapping("/api/v1/admin/plan-reviews")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<LessonPlanResponses.Summary>>> reviewQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ok(collab.reviewQueue(viewer(authentication), page, size));
    }

    @PostMapping("/api/v1/admin/plan-reviews/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request,
            Authentication authentication) {
        collab.decide(id, request.version(), request.decision(), request.note(), viewer(authentication));
        return ok(null);
    }

    private Viewer viewer(Authentication authentication) {
        return viewerResolver.resolve(authentication);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok(ApiResponse.success(body, message()));
    }

    private String message() {
        return messageService.getMessage("plan.success");
    }
}
