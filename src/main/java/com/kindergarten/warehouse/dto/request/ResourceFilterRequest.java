package com.kindergarten.warehouse.dto.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResourceFilterRequest {
    private String keyword;
    private Long topicId;
    private Long categoryId;
    private Long ageGroupId;
    private String topicSlug;
    private String categorySlug;
    private java.util.List<String> ageSlugs;

    private String status;

}
