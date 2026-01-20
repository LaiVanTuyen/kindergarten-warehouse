package com.kindergarten.warehouse.dto.response;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String icon;
    private String description;
    private Long topicCount;

    public CategoryResponse() {
    }

    public CategoryResponse(Long id, String name, String slug, String icon, String description, Long topicCount) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.icon = icon;
        this.description = description;
        this.topicCount = topicCount;
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

        public CategoryResponse build() {
            return new CategoryResponse(id, name, slug, icon, description, topicCount);
        }

        public String toString() {
            return "CategoryResponse.CategoryResponseBuilder(id=" + this.id + ", name=" + this.name + ", slug="
                    + this.slug + ")";
        }
    }
}
