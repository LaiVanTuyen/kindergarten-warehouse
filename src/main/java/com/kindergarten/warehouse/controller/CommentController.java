package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.CreateCommentRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.CommentResponse;
import com.kindergarten.warehouse.service.CommentService;
import com.kindergarten.warehouse.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Set;

import com.kindergarten.warehouse.util.PageableUtils;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private static final Set<String> SORT_FIELDS = Set.of("id", "rating", "createdAt", "updatedAt");

    private final CommentService commentService;
    private final MessageService messageService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @Valid @RequestBody CreateCommentRequest request,
            Principal principal) {

        int rating = request.getRating() != null ? request.getRating() : 5;
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                commentService.createComment(request.getResourceId(), principal.getName(),
                        request.getContent(), rating),
                messageService.getMessage("comment.create.success")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CommentResponse>>> getComments(
            @RequestParam String resourceId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable requestedPageable) {
        Pageable pageable = PageableUtils.sanitize(
                requestedPageable, SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));

        return ResponseEntity.ok(ApiResponse.success(
                commentService.getCommentsByResourceId(resourceId, pageable),
                messageService.getMessage("comment.list.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long id, Principal principal) {
        commentService.deleteComment(id, principal.getName());
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("comment.delete.success")));
    }
}
