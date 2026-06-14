package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TopicRequest {
    @NotBlank(message = "{validation.required}")
    private String name;

    private String slug;

    private String description;

    private Long categoryId;

    private Visibility visibility;

}
