package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.ResourceResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

public interface ResourceService {
    ResourceResponse uploadResource(MultipartFile file, String title, String description,
            Long topicId, java.util.List<Long> ageGroupIds, String username);

    Page<ResourceResponse> getResources(com.kindergarten.warehouse.dto.request.ResourceFilterRequest filterRequest,
            int page, int size);

    void incrementViewCount(String id);

    void deleteResource(String id);
}
