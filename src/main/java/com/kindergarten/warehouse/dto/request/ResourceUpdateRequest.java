package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.ResourceStatus;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class ResourceUpdateRequest {
    @jakarta.validation.constraints.Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;
    @jakarta.validation.constraints.Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;
    private Long topicId;
    private List<Long> ageGroupIds;
    /** null = keep current tags; empty list = clear all tags. */
    private List<String> tags;
    private ResourceStatus status;
    private String fileType;
    private MultipartFile thumbnail;
    private MultipartFile file;
    private String youtubeLink; // Can update to a new link
}
