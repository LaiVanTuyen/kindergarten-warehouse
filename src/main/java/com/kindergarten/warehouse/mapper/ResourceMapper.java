package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceType;
import com.kindergarten.warehouse.service.ResourceStatService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ResourceMapper {

    private final TopicMapper topicMapper;
    private final AgeGroupMapper ageGroupMapper;
    private final ResourceStatService resourceStatService;

    public ResourceMapper(TopicMapper topicMapper, AgeGroupMapper ageGroupMapper,
            @Lazy ResourceStatService resourceStatService) {
        this.topicMapper = topicMapper;
        this.ageGroupMapper = ageGroupMapper;
        this.resourceStatService = resourceStatService;
    }

    /**
     * Map từ projection của danh sách Portal.
     *
     * <p>Khác {@link #toResponse(Resource, boolean, long, long)} ở hai điểm, cả
     * hai đều có chủ đích:
     *
     * <ul>
     *   <li>{@code createdBy} truyền vào từ batch query chỉ select
     *       {@code id, full_name}, <strong>không</strong> hydrate entity
     *       {@code User}. Trường này cần cho cột "Người tải lên" của màn Admin
     *       — màn đó gọi chính endpoint list này, không phải
     *       {@code /admin/resources}.</li>
     *   <li>{@code updatedBy} và {@code topic.createdBy}/{@code topic.updatedBy}
     *       <strong>luôn null</strong> ở list. Đã kiểm tra: không giao diện nào
     *       hiển thị chúng trong danh sách. Đây là quyết định có chủ đích, ghi
     *       ở API_CONTRACT_V2 §0.5 — không phải hệ quả ngẫu nhiên.</li>
     *   <li>{@code ageGroups} truyền vào từ batch query riêng, không lazy-load
     *       theo từng dòng.</li>
     * </ul>
     */
    public ResourceResponse toResponse(
            com.kindergarten.warehouse.repository.projection.ResourceListView view,
            java.util.List<com.kindergarten.warehouse.dto.response.AgeGroupResponse> ageGroups,
            Long topicResourceCount,
            String creatorName,
            boolean isFavorited, long pendingViews, long pendingDownloads) {
        if (view == null) {
            return null;
        }

        String exposedFileUrl = view.getResourceType() == ResourceType.FILE
                ? "/api/v1/resources/" + view.getId() + "/file"
                : view.getFileUrl();

        return ResourceResponse.builder()
                .id(view.getId())
                .title(view.getTitle())
                .slug(view.getSlug())
                .description(view.getDescription())
                .viewsCount(nullSafe(view.getViewsCount()) + pendingViews)
                .fileUrl(exposedFileUrl)
                .thumbnailUrl(view.getThumbnailUrl())
                .resourceType(view.getResourceType())
                .fileType(view.getFileType())
                .fileExtension(view.getFileExtension())
                .fileSize(view.getFileSize())
                .duration(view.getDuration())
                .status(view.getStatus())
                .downloadCount(nullSafe(view.getDownloadCount()) + pendingDownloads)
                .averageRating(view.getAverageRating())
                .topic(topicMapper.toResponse(view.getTopic(), topicResourceCount))
                .ageGroups(ageGroups == null ? java.util.List.of() : ageGroups)
                .visibility(view.getVisibility())
                .rejectionReason(view.getRejectionReason())
                .isFavorited(isFavorited)
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .createdBy(creatorName)
                .build();
    }

    private static long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    public ResourceResponse toResponse(Resource resource, boolean isFavorited) {
        if (resource == null) {
            return null;
        }
        long pendingViews = 0;
        long pendingDownloads = 0;
        try {
            pendingViews = resourceStatService.getPendingViewCount(resource.getId());
            pendingDownloads = resourceStatService.getPendingDownloadCount(resource.getId());
        } catch (Exception ignored) {
        }
        return toResponse(resource, isFavorited, pendingViews, pendingDownloads);
    }

    public ResourceResponse toResponse(Resource resource, boolean isFavorited,
            long pendingViews, long pendingDownloads) {
        if (resource == null) {
            return null;
        }

        // FILE resources are stored privately in MinIO; clients must download
        // through the app-authenticated endpoint. YOUTUBE links remain external.
        String exposedFileUrl = resource.getResourceType() == ResourceType.FILE
                ? "/api/v1/resources/" + resource.getId() + "/file"
                : resource.getFileUrl();

        return ResourceResponse.builder()
                .id(resource.getId())
                .title(resource.getTitle())
                .slug(resource.getSlug())
                .description(resource.getDescription())
                .viewsCount(resource.getViewsCount() + pendingViews)
                .fileUrl(exposedFileUrl)
                .thumbnailUrl(resource.getThumbnailUrl())
                .resourceType(resource.getResourceType())
                .fileType(resource.getFileType())
                .fileExtension(resource.getFileExtension())
                .fileSize(resource.getFileSize())
                .duration(resource.getDuration())
                .status(resource.getStatus())
                .downloadCount(resource.getDownloadCount() + pendingDownloads)
                .averageRating(resource.getAverageRating())
                .topic(topicMapper.toResponse(resource.getTopic()))
                .ageGroups(resource.getAgeGroups().stream()
                        .map(ageGroupMapper::toResponse)
                        .collect(Collectors.toList()))
                .visibility(resource.getVisibility())
                .rejectionReason(resource.getRejectionReason())
                .isFavorited(isFavorited)
                .createdAt(resource.getCreatedAt())
                .updatedAt(resource.getUpdatedAt())
                .createdBy(resource.getCreator() != null ? resource.getCreator().getFullName() : null)
                .updatedBy(resource.getUpdater() != null ? resource.getUpdater().getFullName() : null)
                .build();
    }
}
