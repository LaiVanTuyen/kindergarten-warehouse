package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.response.AgeGroupResponse;
import com.kindergarten.warehouse.mapper.AgeGroupMapper;
import com.kindergarten.warehouse.repository.AgeGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgeGroupService {

    private final AgeGroupRepository ageGroupRepository;
    private final AgeGroupMapper ageGroupMapper;

    public List<AgeGroupResponse> getAllAgeGroups() {
        return ageGroupRepository.findAll().stream()
                .map(ageGroupMapper::toResponse)
                .collect(Collectors.toList());
    }
}
