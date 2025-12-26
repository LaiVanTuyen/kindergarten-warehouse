package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

public class CategoryResponse {
    private Long id;
    private String name;
    private String slug;

    public CategoryResponse() {
    }

    public CategoryResponse(Long id, String name, String slug) {
        this.id = id;
        this.name = name;
        this.slug = slug;
    }

    public static CategoryResponseBuilder builder() {
        return new CategoryResponseBuilder();
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

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public static class CategoryResponseBuilder {
        private Long id;
        private String name;
        private String slug;

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

        public CategoryResponse build() {
            return new CategoryResponse(id, name, slug);
        }

        public String toString() {
            return "CategoryResponse.CategoryResponseBuilder(id=" + this.id + ", name=" + this.name + ", slug="
                    + this.slug + ")";
        }
    }
}
