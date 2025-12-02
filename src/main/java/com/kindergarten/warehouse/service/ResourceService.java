package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.ResourceResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ResourceService {
    ResourceResponse uploadResource(MultipartFile file, String title, String description,
            Long topicId, String username);

    Page<ResourceResponse> getResources(Long topicId, Long categoryId, int page, int size);

    void incrementViewCount(UUID id);

    void deleteResource(UUID id);
}
