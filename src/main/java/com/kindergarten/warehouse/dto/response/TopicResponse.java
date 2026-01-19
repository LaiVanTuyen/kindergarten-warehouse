package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

public class TopicResponse {
    private Long id;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
    private Long resourceCount;

    public TopicResponse() {
    }

    public TopicResponse(Long id, String name, String description, Long categoryId, String categoryName,
            Long resourceCount) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.resourceCount = resourceCount;
    }

    public static TopicResponseBuilder builder() {
        return new TopicResponseBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public static class TopicResponseBuilder {
        private Long id;
        private String name;
        private String description;
        private Long categoryId;
        private String categoryName;
        private Long resourceCount;

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

        public TopicResponse build() {
            return new TopicResponse(id, name, description, categoryId, categoryName, resourceCount);
        }

        public String toString() {
            return "TopicResponse.TopicResponseBuilder(id=" + this.id + ", name=" + this.name + ", description="
                    + this.description + ", categoryId=" + this.categoryId + ", categoryName=" + this.categoryName
                    + ", resourceCount=" + this.resourceCount + ")";
        }
    }
}
