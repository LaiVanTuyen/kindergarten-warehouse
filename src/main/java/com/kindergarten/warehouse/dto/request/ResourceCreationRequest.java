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
    @jakarta.validation.constraints.Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;

    @jakarta.validation.constraints.Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Topic ID is required")
    private Long topicId;

    private List<Long> ageGroupIds;

    private String duration; // Optional: "05:30"
}
