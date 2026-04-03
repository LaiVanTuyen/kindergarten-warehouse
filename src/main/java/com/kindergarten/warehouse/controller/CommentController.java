package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.dto.response.CommentResponse;
import com.kindergarten.warehouse.service.CommentService;
import com.kindergarten.warehouse.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final MessageService messageService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @RequestParam String resourceId,
            @RequestParam String content,
            @RequestParam(defaultValue = "5") int rating,
            Principal principal) {
        
        return ResponseEntity.ok(ApiResponse.success(
                commentService.createComment(resourceId, principal.getName(), content, rating),
                messageService.getMessage("comment.create.success")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CommentResponse>>> getComments(
            @RequestParam String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        return ResponseEntity.ok(ApiResponse.success(
                commentService.getCommentsByResourceId(resourceId, page, size),
                messageService.getMessage("comment.list.success")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long id, Principal principal) {
        commentService.deleteComment(id, principal.getName());
        return ResponseEntity.ok(ApiResponse.success(null, messageService.getMessage("comment.delete.success")));
    }
}
