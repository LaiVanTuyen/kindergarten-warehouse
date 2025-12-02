package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TopicRequest {
    @NotBlank(message = "{validation.required}")
    private String name;

    private String description;
}
