package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class ResourceCreationRequest {
    
    private MultipartFile file; // Optional if youtubeLink is present

    private String youtubeLink; // Optional if file is present

    private MultipartFile thumbnail;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Topic ID is required")
    private Long topicId;

    private List<Long> ageGroupIds;

    private String duration; // Optional: "05:30"
}
