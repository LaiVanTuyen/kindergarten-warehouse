package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

public class ResourceResponse {
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

    public ResourceResponse() {
    }

    public ResourceResponse(String id, String title, String slug, String description, Long viewsCount,
            LocalDateTime createdAt, String fileUrl, String thumbnailUrl, String fileType, String fileExtension,
            Long topicId, String topicName, Long fileSize, String createdBy,
            java.util.List<AgeGroupResponse> ageGroups,
            java.util.List<String> highlights, Boolean isFavorited, Double averageRating,
            String status, Long downloadCount) {
        this.id = id;
        this.title = title;
        this.slug = slug;
        this.description = description;
        this.viewsCount = viewsCount;
        this.createdAt = createdAt;
        this.fileUrl = fileUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.fileType = fileType;
        this.fileExtension = fileExtension;
        this.topicId = topicId;
        this.topicName = topicName;
        this.fileSize = fileSize;
        this.createdBy = createdBy;
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

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getViewsCount() {
        return viewsCount;
    }

    public void setViewsCount(Long viewsCount) {
        this.viewsCount = viewsCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public java.util.List<AgeGroupResponse> getAgeGroups() {
        return ageGroups;
    }

    public void setAgeGroups(java.util.List<AgeGroupResponse> ageGroups) {
        this.ageGroups = ageGroups;
    }

    public java.util.List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(java.util.List<String> highlights) {
        this.highlights = highlights;
    }

    public Boolean getIsFavorited() {
        return isFavorited;
    }

    public void setIsFavorited(Boolean isFavorited) {
        this.isFavorited = isFavorited;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
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

        public ResourceResponse build() {
            return new ResourceResponse(id, title, slug, description, viewsCount, createdAt, fileUrl, thumbnailUrl,
                    fileType, fileExtension, topicId, topicName, fileSize, createdBy, ageGroups, highlights,
                    isFavorited, averageRating, status, downloadCount);
        }
    }
}
