package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.LessonPlanRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.LessonPlanResponses;
import com.kindergarten.warehouse.security.Viewer;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.LessonPlanService;
import com.kindergarten.warehouse.service.MessageService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Kế hoạch hoạt động" for teachers. See {@link LessonPlanService} for access rules. */
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TEACHER', 'ADMIN')")
public class LessonPlanController {

    private final LessonPlanService planService;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<LessonPlanResponses.Summary>>> mine(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return ok(planService.listMine(viewer(authentication), keyword, page, size));
    }

    @GetMapping("/shared")
    public ResponseEntity<ApiResponse<Page<LessonPlanResponses.Summary>>> shared(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return ok(planService.listShared(viewer(authentication), keyword, ageGroupId, page, size));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<Page<LessonPlanResponses.Summary>>> templates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ageGroupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Authentication authentication) {
        return ok(planService.listTemplates(viewer(authentication), keyword, ageGroupId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LessonPlanResponses.Detail>> get(
            @PathVariable Long id, Authentication authentication) {
        return ok(planService.get(id, viewer(authentication)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LessonPlanResponses.Summary>> create(
            @Valid @RequestBody LessonPlanRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                planService.create(request, viewer(authentication)), message()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LessonPlanResponses.Summary>> update(
            @PathVariable Long id, @Valid @RequestBody LessonPlanRequest request,
            Authentication authentication) {
        return ok(planService.update(id, request, viewer(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, Authentication authentication) {
        planService.delete(id, viewer(authentication));
        return ok(null);
    }

    /** Copy any readable plan — own older plan, a colleague's shared plan, or a template. */
    @PostMapping("/{id}/copy")
    public ResponseEntity<ApiResponse<LessonPlanResponses.Summary>> copy(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                planService.copy(id, viewer(authentication)), message()));
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
