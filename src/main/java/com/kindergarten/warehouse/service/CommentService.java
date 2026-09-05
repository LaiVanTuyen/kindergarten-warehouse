package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.CommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CommentService {
    CommentResponse createComment(String resourceId, String username, String content, int rating);
    Page<CommentResponse> getCommentsByResourceId(String resourceId, Pageable pageable);
    void deleteComment(Long commentId, String username);
}
