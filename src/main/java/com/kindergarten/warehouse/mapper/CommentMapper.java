package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.CommentResponse;
import com.kindergarten.warehouse.entity.Comment;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {
    public CommentResponse toResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .rating(comment.getRating())
                .username(comment.getUser().getUsername())
                .userAvatar(comment.getUser().getAvatarUrl())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
