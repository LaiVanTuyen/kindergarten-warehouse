package com.kindergarten.warehouse.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class ResourceResponse extends BaseResponse {
    private String id;
    private String title;
    private String slug;
    private String description;
    private Long viewsCount;
    // createdAt moved to BaseResponse
    private String fileUrl;
    private String thumbnailUrl;
    private String fileType;
    private String fileExtension;
    private Long topicId;
    private String topicName;
    private Long fileSize;
    // createdBy moved to BaseResponse
    private java.util.List<AgeGroupResponse> ageGroups;
    private java.util.List<String> highlights;
    private Boolean isFavorited;
    private Double averageRating;
    private String status;
    private Long downloadCount;

    public ResourceResponse() {
        super();
    }

    public ResourceResponse(String id, String title, String slug, String description, Long viewsCount,
            LocalDateTime createdAt, String fileUrl, String thumbnailUrl, String fileType, String fileExtension,
            Long topicId, String topicName, Long fileSize, String createdBy,
            java.util.List<AgeGroupResponse> ageGroups,
            java.util.List<String> highlights, Boolean isFavorited, Double averageRating,
            String status, Long downloadCount,
            LocalDateTime updatedAt, String updatedBy) {
        super(createdAt, updatedAt, createdBy, updatedBy);
        this.id = id;
        this.title = title;
        this.slug = slug;
        this.description = description;
        this.viewsCount = viewsCount;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.fileType = fileType;
        this.fileExtension = fileExtension;
        this.topicId = topicId;
        this.topicName = topicName;
        this.fileSize = fileSize;
        this.ageGroups = ageGroups;
        this.highlights = highlights;
        this.isFavorited = isFavorited;
        this.averageRating = averageRating;
        this.status = status;
        this.downloadCount = downloadCount;
    }

    public static ResourceResponseBuilder builder() {
        return new ResourceResponseBuilder();
    }

    public static class ResourceResponseBuilder {
        private String id;
        private String title;
        private String slug;
        private String description;
        private Long viewsCount;
        private LocalDateTime createdAt;
        private String fileUrl;
        private String thumbnailUrl;
        private String fileType;
        private String fileExtension;
        private Long topicId;
        private String topicName;
        private Long fileSize;
        private String createdBy;
        private java.util.List<AgeGroupResponse> ageGroups;
        private java.util.List<String> highlights;
        private Boolean isFavorited;
        private Double averageRating;
        private String status;
        private Long downloadCount;
        private LocalDateTime updatedAt;
        private String updatedBy;

        ResourceResponseBuilder() {
        }

        public ResourceResponseBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ResourceResponseBuilder title(String title) {
            this.title = title;
            return this;
        }

        public ResourceResponseBuilder slug(String slug) {
            this.slug = slug;
            return this;
        }

        public ResourceResponseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ResourceResponseBuilder viewsCount(Long viewsCount) {
            this.viewsCount = viewsCount;
            return this;
        }

        public ResourceResponseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ResourceResponseBuilder fileUrl(String fileUrl) {
            this.fileUrl = fileUrl;
            return this;
        }

        public ResourceResponseBuilder thumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public ResourceResponseBuilder fileType(String fileType) {
            this.fileType = fileType;
            return this;
        }

        public ResourceResponseBuilder fileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
            return this;
        }

        public ResourceResponseBuilder topicId(Long topicId) {
            this.topicId = topicId;
            return this;
        }

        public ResourceResponseBuilder topicName(String topicName) {
            this.topicName = topicName;
            return this;
        }

        public ResourceResponseBuilder fileSize(Long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public ResourceResponseBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public ResourceResponseBuilder ageGroups(java.util.List<AgeGroupResponse> ageGroups) {
            this.ageGroups = ageGroups;
            return this;
        }

        public ResourceResponseBuilder highlights(java.util.List<String> highlights) {
            this.highlights = highlights;
            return this;
        }

        public ResourceResponseBuilder isFavorited(Boolean isFavorited) {
            this.isFavorited = isFavorited;
            return this;
        }

        public ResourceResponseBuilder averageRating(Double averageRating) {
            this.averageRating = averageRating;
            return this;
        }

        public ResourceResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public ResourceResponseBuilder downloadCount(Long downloadCount) {
            this.downloadCount = downloadCount;
            return this;
        }

        public ResourceResponseBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ResourceResponseBuilder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public ResourceResponse build() {
            return new ResourceResponse(id, title, slug, description, viewsCount, createdAt, fileUrl, thumbnailUrl,
                    fileType, fileExtension, topicId, topicName, fileSize, createdBy, ageGroups, highlights,
                    isFavorited, averageRating, status, downloadCount, updatedAt, updatedBy);
        }
    }
}
