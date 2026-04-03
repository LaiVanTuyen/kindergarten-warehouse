package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.CommentResponse;
import org.springframework.data.domain.Page;

public interface CommentService {
    CommentResponse createComment(String resourceId, String username, String content, int rating);
    Page<CommentResponse> getCommentsByResourceId(String resourceId, int page, int size);
    void deleteComment(Long commentId, String username);
}
