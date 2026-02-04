package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CommentResponse {
    private Long id;
    private String content;
    private int rating;
    private String username;
    private String userAvatar;
    private LocalDateTime createdAt;
}
