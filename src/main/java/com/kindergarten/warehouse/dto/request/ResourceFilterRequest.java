package com.kindergarten.warehouse.dto.request;

import lombok.Data;

@Data
public class ResourceFilterRequest {
    private String keyword;
    private Long topicId;
    private Long categoryId;
    private Long ageGroupId;
}
