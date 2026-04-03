package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class CategoryRequest {
    @NotBlank(message = "{validation.required}")
    private String name;

    @NotBlank(message = "{validation.required}")
    private String slug;

    private String description;

    private Boolean isActive;

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
