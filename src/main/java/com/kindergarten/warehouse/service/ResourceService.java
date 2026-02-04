package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.ResourceCreationRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.request.ResourceUpdateRequest;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

public interface ResourceService {
        ResourceResponse uploadResource(ResourceCreationRequest request, String username);

        Page<ResourceResponse> getResources(ResourceFilterRequest filterRequest, int page, int size);

        void incrementViewCount(String id, String ipAddress);

        void deleteResource(String id);

        ResourceResponse getResourceBySlug(String slug);

        void incrementDownloadCount(String id);

        ResourceResponse updateResource(String id, ResourceUpdateRequest request);
        
        void toggleFavorite(String resourceId, String username);

        void restoreResource(String id);

        String updateThumbnail(String id, MultipartFile thumbnail);
}
