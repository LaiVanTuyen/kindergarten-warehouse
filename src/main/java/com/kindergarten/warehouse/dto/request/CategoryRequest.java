package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank(message = "{validation.required}")
    private String name;

    @NotBlank(message = "{validation.required}")
    private String slug;
}
