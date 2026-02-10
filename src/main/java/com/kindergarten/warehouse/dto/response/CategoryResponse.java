package com.kindergarten.warehouse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse extends BaseResponse {
    private Long id;
    private String name;
    private String slug;
    private String icon;
    private String description;
    private Long topicCount;
    private Boolean isActive;
}
