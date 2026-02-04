package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceResponse {
    private String id;
    private String title;
    private String slug;
    private String description;
    private Long viewsCount;
    private String fileUrl;
    private String thumbnailUrl;
    private ResourceType resourceType; // FILE, YOUTUBE
    private String fileType; // VIDEO, PDF...
    private String fileExtension;
    private Long fileSize;
    private ResourceStatus status;
    private Long downloadCount;
    private Double averageRating;
    private TopicResponse topic;
    private List<AgeGroupResponse> ageGroups;
    private Boolean isFavorited;
    private LocalDateTime createdAt;
    private String createdBy; // Username
}
