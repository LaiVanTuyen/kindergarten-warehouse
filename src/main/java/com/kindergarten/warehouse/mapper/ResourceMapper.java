package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.Resource;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ResourceMapper {

    private final TopicMapper topicMapper;
    private final AgeGroupMapper ageGroupMapper;
    private final com.kindergarten.warehouse.service.ResourceStatService resourceStatService;

    public ResourceMapper(TopicMapper topicMapper, AgeGroupMapper ageGroupMapper,
            @org.springframework.context.annotation.Lazy com.kindergarten.warehouse.service.ResourceStatService resourceStatService) {
        this.topicMapper = topicMapper;
        this.ageGroupMapper = ageGroupMapper;
        this.resourceStatService = resourceStatService;
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

        return ResourceResponse.builder()
                .id(resource.getId())
                .title(resource.getTitle())
                .slug(resource.getSlug())
                .description(resource.getDescription())
                .viewsCount(resource.getViewsCount() + pendingViews)
                .fileUrl(resource.getFileUrl())
                .thumbnailUrl(resource.getThumbnailUrl())
                .resourceType(resource.getResourceType())
                .fileType(resource.getFileType())
                .fileExtension(resource.getFileExtension())
                .fileSize(resource.getFileSize())
                .duration(resource.getDuration()) // Mapped duration
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
