package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.AgeGroupResponse;
import com.kindergarten.warehouse.entity.AgeGroup;
import org.springframework.stereotype.Component;

@Component
public class AgeGroupMapper {

    public AgeGroupResponse toResponse(AgeGroup ageGroup) {
        if (ageGroup == null) {
            return null;
        }

        return AgeGroupResponse.builder()
                .id(ageGroup.getId())
                .name(ageGroup.getName())
                .minAge(ageGroup.getMinAge())
                .maxAge(ageGroup.getMaxAge())
                .description(ageGroup.getDescription())
                .build();
    }
}
