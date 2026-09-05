package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.CommentResponse;
import com.kindergarten.warehouse.service.CommentService;
import com.kindergarten.warehouse.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    /** API_CONTRACT_V2 §0.5: whitelist sort RIENG cua endpoint nay. */
    private static final java.util.Set<String> SORT_FIELDS = java.util.Set.of("id", "createdAt", "updatedAt");

    private static final org.springframework.data.domain.Sort DEFAULT_SORT =
            org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt");

    private final CommentService commentService;
    private final MessageService messageService;
    private final com.kindergarten.warehouse.security.ViewerResolver viewerResolver;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @RequestParam String resourceId,
            @RequestParam String content,
            @RequestParam(defaultValue = "5") int rating,
            Principal principal) {
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                commentService.createComment(resourceId, principal.getName(), content, rating),
                messageService.getMessage("comment.create.success")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CommentResponse>>> getComments(
            @RequestParam String resourceId,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable requestedPageable,
            org.springframework.security.core.Authentication authentication) {

        org.springframework.data.domain.Pageable pageable =
                com.kindergarten.warehouse.util.PageableUtils.sanitize(requestedPageable, SORT_FIELDS, DEFAULT_SORT);

        return ResponseEntity.ok(ApiResponse.success(
                commentService.getCommentsByResourceId(resourceId, pageable,
                        viewerResolver.resolve(authentication)),
                messageService.getMessage("comment.list.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long id, Principal principal) {
        commentService.deleteComment(id, principal.getName());
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("comment.delete.success")));
    }
}
