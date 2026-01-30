package com.kindergarten.warehouse.dto.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CategoryResponse extends BaseResponse {
    private Long id;
    private String name;
    private String slug;
    private String icon;
    private String description;
    private Long topicCount;
    private Boolean isActive;

    public CategoryResponse() {
        super();
    }

    public CategoryResponse(Long id, String name, String slug, String icon, String description, Long topicCount,
            Boolean isActive,
            java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt, String createdBy, String updatedBy) {
        super(createdAt, updatedAt, createdBy, updatedBy);
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.icon = icon;
        this.description = description;
        this.topicCount = topicCount;
        this.isActive = isActive;
    }

    public static CategoryResponseBuilder builder() {
        return new CategoryResponseBuilder();
    }

    public static class CategoryResponseBuilder {
        private Long id;
        private String name;
        private String slug;
        private String icon;
        private String description;
        private Long topicCount;
        private Boolean isActive;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;

        CategoryResponseBuilder() {
        }

        public CategoryResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public CategoryResponseBuilder name(String name) {
            this.name = name;
            return this;
        }

        public CategoryResponseBuilder slug(String slug) {
            this.slug = slug;
            return this;
        }

        public CategoryResponseBuilder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public CategoryResponseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public CategoryResponseBuilder topicCount(Long topicCount) {
            this.topicCount = topicCount;
            return this;
        }

        public CategoryResponseBuilder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public CategoryResponseBuilder createdAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public CategoryResponseBuilder updatedAt(java.time.LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public CategoryResponseBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public CategoryResponseBuilder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public CategoryResponse build() {
            return new CategoryResponse(id, name, slug, icon, description, topicCount, isActive, createdAt, updatedAt,
                    createdBy,
                    updatedBy);
        }

        public String toString() {
            return "CategoryResponse.CategoryResponseBuilder(id=" + this.id + ", name=" + this.name + ", slug="
                    + this.slug + ")";
        }
    }
}
