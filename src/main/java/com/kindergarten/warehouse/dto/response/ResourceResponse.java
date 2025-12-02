package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.ResourceType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ResourceResponse {
    private UUID id;
    private String title;
    private String description;
    private Long viewsCount;
    private LocalDateTime createdAt;
    private String fileUrl;
    private ResourceType fileType;
    private String fileExtension;
    private Long topicId;
    private String topicName;
    private Long fileSize;
    private String createdBy;
}
