package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class CategoryRequest {
    @NotBlank(message = "{validation.required}")
    private String name;

    @NotBlank(message = "{validation.required}")
    private String slug;

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
}
