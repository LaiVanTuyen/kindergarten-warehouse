package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.BulkResourceRequest;
import com.kindergarten.warehouse.dto.request.ResourceCreationRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.request.ResourceUpdateRequest;
import com.kindergarten.warehouse.dto.request.VisibilityUpdateRequest;
import com.kindergarten.warehouse.dto.response.BulkOperationResponse;
import com.kindergarten.warehouse.dto.response.FileDownloadInfo;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResourceService {
        ResourceResponse uploadResource(ResourceCreationRequest request, String username);

        Page<ResourceResponse> getPortalResources(ResourceFilterRequest filterRequest, Pageable pageable);

        Page<ResourceResponse> getAdminResources(ResourceFilterRequest filterRequest, Pageable pageable);

        Page<ResourceResponse> getMyResources(ResourceFilterRequest filterRequest, Pageable pageable, String username);

        ResourceResponse getResourceBySlug(String slug);

        FileDownloadInfo getResourceFileInfo(String id) throws Exception;

        void incrementViewCount(String id, String ipAddress);

        void incrementDownloadCount(String id);

        ResourceResponse updateResource(String id, ResourceUpdateRequest request, String username);

        ResourceResponse updateVisibility(String id, VisibilityUpdateRequest request, String username);

        String updateThumbnail(String id, MultipartFile thumbnail, String username);

        boolean toggleFavorite(String resourceId, String username);

        void deleteResource(String id, String username, boolean hard);

        void deleteResources(List<String> ids, String username, boolean hard);

        void restoreResource(String id, String username);

        void restoreResources(List<String> ids, String username);

        ResourceResponse approveResource(String id, String username);

        ResourceResponse rejectResource(String id, String reason, String username);

        BulkOperationResponse bulkApprove(BulkResourceRequest request, String username);

        BulkOperationResponse bulkReject(BulkResourceRequest request, String username);
}
