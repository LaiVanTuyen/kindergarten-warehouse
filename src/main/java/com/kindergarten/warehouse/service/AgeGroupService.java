package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.AgeGroupResponse;
import com.kindergarten.warehouse.entity.AgeGroup;
import com.kindergarten.warehouse.repository.AgeGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgeGroupService {

    private final AgeGroupRepository ageGroupRepository;

    public List<AgeGroupResponse> getAllAgeGroups() {
        return ageGroupRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private AgeGroupResponse mapToResponse(AgeGroup ageGroup) {
        return AgeGroupResponse.builder()
                .id(ageGroup.getId())
                .name(ageGroup.getName())
                .minAge(ageGroup.getMinAge())
                .maxAge(ageGroup.getMaxAge())
                .description(ageGroup.getDescription())
                .build();
    }
}
