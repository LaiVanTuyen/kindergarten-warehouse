package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ResourceResponse {
    private String id;
    private String title;
    private String description;
    private Long viewsCount;
    private LocalDateTime createdAt;
    private String fileUrl;
    private String thumbnailUrl;
    private String fileType;
    private String fileExtension;
    private Long topicId;
    private String topicName;
    private Long fileSize;
    private String createdBy;
}
