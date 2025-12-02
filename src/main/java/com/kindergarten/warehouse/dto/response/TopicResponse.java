package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicResponse {
    private Long id;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
}
