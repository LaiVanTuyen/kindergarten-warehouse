package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.ResourceStatus;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class ResourceUpdateRequest {
    private String title;
    private String description;
    private Long topicId;
    private List<Long> ageGroupIds;
    private ResourceStatus status;
    private String fileType;
    private MultipartFile thumbnail;
    private String youtubeLink; // Can update to a new link
}
