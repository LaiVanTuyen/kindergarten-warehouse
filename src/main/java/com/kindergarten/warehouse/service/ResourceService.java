package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.ResourceCreationRequest;
import com.kindergarten.warehouse.dto.request.BulkResourceRequest;
import com.kindergarten.warehouse.dto.request.ResourceFilterRequest;
import com.kindergarten.warehouse.dto.request.ResourceUpdateRequest;
import com.kindergarten.warehouse.dto.response.BulkOperationResponse;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

public interface ResourceService {
        ResourceResponse uploadResource(ResourceCreationRequest request, String username);

        Page<ResourceResponse> getPortalResources(ResourceFilterRequest filterRequest, int page, int size);

        Page<ResourceResponse> getAdminResources(ResourceFilterRequest filterRequest, int page, int size);

        Page<ResourceResponse> getMyResources(ResourceFilterRequest filterRequest, int page, int size, String username);

        void incrementViewCount(String id, String ipAddress);

        void deleteResource(String id, String username, boolean hard);

        ResourceResponse getResourceBySlug(String slug);

        void incrementDownloadCount(String id);

        ResourceResponse updateResource(String id, ResourceUpdateRequest request, String username);

        boolean toggleFavorite(String resourceId, String username);

        void restoreResource(String id, String username);

        void deleteResources(java.util.List<String> ids, String username, boolean hard);

        void restoreResources(java.util.List<String> ids, String username);

        String updateThumbnail(String id, MultipartFile thumbnail, String username);

        // ✅ CRITICAL FIX #1: Get file info for download
        com.kindergarten.warehouse.dto.response.FileDownloadInfo getResourceFileInfo(String id) throws Exception;

        ResourceResponse updateVisibility(String id,
                        com.kindergarten.warehouse.dto.request.VisibilityUpdateRequest request, String username);

        ResourceResponse approveResource(String id, String username);

        ResourceResponse rejectResource(String id, String reason, String username);

        BulkOperationResponse bulkApprove(BulkResourceRequest request, String username);

        BulkOperationResponse bulkReject(BulkResourceRequest request, String username);
}
