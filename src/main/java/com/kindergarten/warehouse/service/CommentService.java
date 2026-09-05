package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.CommentResponse;
import org.springframework.data.domain.Page;

public interface CommentService {
    CommentResponse createComment(String resourceId, String username, String content, int rating);
    Page<CommentResponse> getCommentsByResourceId(String resourceId,
            org.springframework.data.domain.Pageable pageable,
            com.kindergarten.warehouse.security.Viewer viewer);
    void deleteComment(Long commentId, String username);
}
