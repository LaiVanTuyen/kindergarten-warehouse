package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.AiRequests;
import com.kindergarten.warehouse.dto.response.AiResponses;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.security.ViewerResolver;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.service.ai.AiAssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Teacher AI assistant. Every result is a suggestion; nothing here writes to the database. */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('TEACHER', 'ADMIN')")
public class AiAssistantController {

    private final AiAssistantService assistant;
    private final MessageService messageService;
    private final ViewerResolver viewerResolver;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<AiResponses.Status>> status(Authentication authentication) {
        return ok(assistant.status(viewerResolver.resolve(authentication)));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<AiResponses.SearchResult>> search(
            @Valid @RequestBody AiRequests.Search request, Authentication authentication) {
        return ok(assistant.search(request, viewerResolver.resolve(authentication)));
    }

    @PostMapping("/plan-draft")
    public ResponseEntity<ApiResponse<AiResponses.PlanDraft>> draft(
            @Valid @RequestBody AiRequests.Draft request, Authentication authentication) {
        return ok(assistant.draftPlan(request, viewerResolver.resolve(authentication)));
    }

    @PostMapping("/plan-adjust")
    public ResponseEntity<ApiResponse<AiResponses.PlanDraft>> adjust(
            @Valid @RequestBody AiRequests.Adjust request, Authentication authentication) {
        return ok(assistant.adjustPlan(request, viewerResolver.resolve(authentication)));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok(ApiResponse.success(body, messageService.getMessage("ai.success")));
    }
}
