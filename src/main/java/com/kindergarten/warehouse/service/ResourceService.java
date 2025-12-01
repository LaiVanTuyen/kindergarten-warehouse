package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

public interface ResourceService {
    Resource uploadResource(MultipartFile file, String title, String description, Long topicId);

    Page<Resource> getResources(Long topicId, int page, int size);

    void deleteResource(Long id);
}
