package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AgeGroupRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Slug is required")
    private String slug;

    @NotNull(message = "Min age is required")
    @Min(value = 0, message = "Min age must be >= 0")
    private Integer minAge;

    @NotNull(message = "Max age is required")
    @Min(value = 0, message = "Max age must be >= 0")
    private Integer maxAge;

    private String description;
}
