package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.AgeGroupResponse;
import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.service.MinioStorageService;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ResourceMapper {

    private final MinioStorageService minioStorageService;

    public ResourceMapper(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
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
                .createdAt(resource.getCreatedAt())
                .fileUrl(minioStorageService.getPresignedUrl(extractKeyFromUrl(resource.getFileUrl())))
                .thumbnailUrl(resource.getThumbnailUrl())
                .fileType(resource.getFileType())
                .fileExtension(resource.getFileExtension())
                .topicId(resource.getTopic().getId())
                .topicName(resource.getTopic().getName())
                .fileSize(resource.getFileSize())
                .createdBy(resource.getCreator() != null ? resource.getCreator().getFullName() : null)
                .ageGroups(resource.getAgeGroups().stream()
                        .map(ag -> AgeGroupResponse.builder()
                                .id(ag.getId())
                                .name(ag.getName())
                                .slug(ag.getSlug())
                                .minAge(ag.getMinAge())
                                .maxAge(ag.getMaxAge())
                                .description(ag.getDescription())
                                .build())
                        .collect(Collectors.toList()))
                .highlights(resource.getHighlights())
                .isFavorited(isFavorited)
                .averageRating(resource.getAverageRating() != null ? resource.getAverageRating() : 0.0)
                .status(resource.getStatus().name())
                .downloadCount(resource.getDownloadCount())
                .createdAt(resource.getCreatedAt())
                .updatedAt(resource.getUpdatedAt())
                .createdBy(resource.getCreator() != null ? resource.getCreator().getFullName() : null)
                .updatedBy(resource.getUpdater() != null ? resource.getUpdater().getFullName() : null)
                .build();
    }

    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl == null)
            return null;
        if (fileUrl.contains("/resources/")) {
            return fileUrl.substring(fileUrl.indexOf("resources/"));
        }
        return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
    }
}
