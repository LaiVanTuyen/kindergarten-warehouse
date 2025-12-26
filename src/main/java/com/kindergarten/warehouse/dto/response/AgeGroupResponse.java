package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgeGroupResponse {
    private Long id;
    private String name;
    private Integer minAge;
    private Integer maxAge;
    private String description;
}
