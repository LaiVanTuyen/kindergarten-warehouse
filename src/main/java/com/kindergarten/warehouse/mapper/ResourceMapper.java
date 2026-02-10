package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.Resource;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ResourceMapper {

    private final TopicMapper topicMapper;
    private final AgeGroupMapper ageGroupMapper;

    public ResourceMapper(TopicMapper topicMapper, AgeGroupMapper ageGroupMapper) {
        this.topicMapper = topicMapper;
        this.ageGroupMapper = ageGroupMapper;
    }

    public ResourceResponse toResponse(Resource resource, boolean isFavorited) {
        if (resource == null) {
            return null;
        }

        return ResourceResponse.builder()
                .id(resource.getId())
                .title(resource.getTitle())
                .slug(resource.getSlug())
                .description(resource.getDescription())
                .viewsCount(resource.getViewsCount())
                .fileUrl(resource.getFileUrl())
                .thumbnailUrl(resource.getThumbnailUrl())
                .resourceType(resource.getResourceType())
                .fileType(resource.getFileType())
                .fileExtension(resource.getFileExtension())
                .fileSize(resource.getFileSize())
                .status(resource.getStatus())
                .downloadCount(resource.getDownloadCount())
                .averageRating(resource.getAverageRating())
                .topic(topicMapper.toResponse(resource.getTopic()))
                .ageGroups(resource.getAgeGroups().stream()
                        .map(ageGroupMapper::toResponse)
                        .collect(Collectors.toList()))
                .isFavorited(isFavorited)
                .createdAt(resource.getCreatedAt())
                .updatedAt(resource.getUpdatedAt())
                .createdBy(resource.getCreator() != null ? resource.getCreator().getFullName() : null)
                .updatedBy(resource.getUpdater() != null ? resource.getUpdater().getFullName() : null)
                .build();
    }
}
