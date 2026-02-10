package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.ResourceType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.List;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ResourceResponse extends BaseResponse {
    private String id;
    private String title;
    private String slug;
    private String description;
    private Long viewsCount;
    private String fileUrl;
    private String thumbnailUrl;
    private ResourceType resourceType;
    private String fileType;
    private String fileExtension;
    private Long fileSize;
    private ResourceStatus status;
    private Long downloadCount;
    private Double averageRating;
    private TopicResponse topic;
    private List<AgeGroupResponse> ageGroups;
    private Boolean isFavorited;
}
