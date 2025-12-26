package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

public class AgeGroupResponse {
    private Long id;
    private String name;
    private String slug;
    private Integer minAge;
    private Integer maxAge;
    private String description;

    public AgeGroupResponse() {
    }

    public AgeGroupResponse(Long id, String name, String slug, Integer minAge, Integer maxAge, String description) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.description = description;
    }

    public static AgeGroupResponseBuilder builder() {
        return new AgeGroupResponseBuilder();
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

    public Integer getMinAge() {
        return minAge;
    }

    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static class AgeGroupResponseBuilder {
        private Long id;
        private String name;
        private String slug;
        private Integer minAge;
        private Integer maxAge;
        private String description;

        AgeGroupResponseBuilder() {
        }

        public AgeGroupResponseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public AgeGroupResponseBuilder name(String name) {
            this.name = name;
            return this;
        }

        public AgeGroupResponseBuilder slug(String slug) {
            this.slug = slug;
            return this;
        }

        public AgeGroupResponseBuilder minAge(Integer minAge) {
            this.minAge = minAge;
            return this;
        }

        public AgeGroupResponseBuilder maxAge(Integer maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public AgeGroupResponseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public AgeGroupResponse build() {
            return new AgeGroupResponse(id, name, slug, minAge, maxAge, description);
        }

        public String toString() {
            return "AgeGroupResponse.AgeGroupResponseBuilder(id=" + this.id + ", name=" + this.name + ", slug="
                    + this.slug + ", minAge=" + this.minAge + ", maxAge=" + this.maxAge + ", description="
                    + this.description + ")";
        }
    }
}
