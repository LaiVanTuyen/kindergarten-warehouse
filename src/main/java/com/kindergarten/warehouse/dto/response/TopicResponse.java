package com.kindergarten.warehouse.dto.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TopicResponse extends BaseResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Long resourceCount;
    private Boolean isActive;

    public TopicResponse() {
        super();
    }

    public TopicResponse(Long id, String name, String slug, String description, Long categoryId, String categoryName,
            Long resourceCount, Boolean isActive, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt,
            String createdBy,
            String updatedBy) {
        super(createdAt, updatedAt, createdBy, updatedBy);
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.resourceCount = resourceCount;
        this.isActive = isActive;
    }

    public static TopicResponseBuilder builder() {
        return new TopicResponseBuilder();
    }

    public static class TopicResponseBuilder {
        private Long id;
        private String name;
        private String slug;
        private String description;
        private Long categoryId;
        private String categoryName;
        private Long resourceCount;
        private Boolean isActive;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;

        TopicResponseBuilder() {
        }

        public TopicResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public TopicResponseBuilder name(String name) {
            this.name = name;
            return this;
        }

        public TopicResponseBuilder slug(String slug) {
            this.slug = slug;
            return this;
        }

        public TopicResponseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public TopicResponseBuilder categoryId(Long categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public TopicResponseBuilder categoryName(String categoryName) {
            this.categoryName = categoryName;
            return this;
        }

        public TopicResponseBuilder resourceCount(Long resourceCount) {
            this.resourceCount = resourceCount;
            return this;
        }

        public TopicResponseBuilder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public TopicResponseBuilder createdAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TopicResponseBuilder updatedAt(java.time.LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public TopicResponseBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public TopicResponseBuilder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public TopicResponse build() {
            return new TopicResponse(id, name, slug, description, categoryId, categoryName, resourceCount, isActive,
                    createdAt,
                    updatedAt, createdBy, updatedBy);
        }

        public String toString() {
            return "TopicResponse.TopicResponseBuilder(id=" + this.id + ", name=" + this.name + ", slug=" + this.slug
                    + ", description="
                    + this.description + ", categoryId=" + this.categoryId + ", categoryName=" + this.categoryName
                    + ", resourceCount=" + this.resourceCount + ")";
        }
    }
}
