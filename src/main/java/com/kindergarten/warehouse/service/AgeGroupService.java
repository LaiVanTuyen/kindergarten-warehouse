package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.request.AgeGroupRequest;
import com.kindergarten.warehouse.dto.response.AgeGroupResponse;
import com.kindergarten.warehouse.entity.AgeGroup;
import com.kindergarten.warehouse.entity.AuditAction;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.AgeGroupMapper;
import com.kindergarten.warehouse.repository.AgeGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AgeGroupService {

    private final AgeGroupRepository ageGroupRepository;
    private final AgeGroupMapper ageGroupMapper;

    @Transactional(readOnly = true)
    public List<AgeGroupResponse> getAllAgeGroups() {
        return ageGroupRepository.findAll().stream()
                .map(ageGroupMapper::toResponse)
                .collect(Collectors.toList());
    }

    @LogAction(action = AuditAction.CREATE, description = "Created age group", target = "AGE_GROUP")
    public AgeGroupResponse createAgeGroup(AgeGroupRequest request) {
        if (ageGroupRepository.existsBySlug(request.getSlug())) {
            throw new AppException(ErrorCode.DUPLICATE_SLUG);
        }

        AgeGroup ageGroup = new AgeGroup();
        ageGroup.setName(request.getName());
        ageGroup.setSlug(request.getSlug());
        ageGroup.setMinAge(request.getMinAge());
        ageGroup.setMaxAge(request.getMaxAge());
        ageGroup.setDescription(request.getDescription());

        return ageGroupMapper.toResponse(ageGroupRepository.save(ageGroup));
    }

    @LogAction(action = AuditAction.UPDATE, description = "Updated age group", target = "AGE_GROUP")
    public AgeGroupResponse updateAgeGroup(Long id, AgeGroupRequest request) {
        AgeGroup ageGroup = ageGroupRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.AGE_GROUP_NOT_FOUND));

        if (!ageGroup.getSlug().equals(request.getSlug()) && ageGroupRepository.existsBySlug(request.getSlug())) {
            throw new AppException(ErrorCode.DUPLICATE_SLUG);
        }

        ageGroup.setName(request.getName());
        ageGroup.setSlug(request.getSlug());
        ageGroup.setMinAge(request.getMinAge());
        ageGroup.setMaxAge(request.getMaxAge());
        ageGroup.setDescription(request.getDescription());

        return ageGroupMapper.toResponse(ageGroupRepository.save(ageGroup));
    }

    @LogAction(action = AuditAction.DELETE, description = "Deleted age group", target = "AGE_GROUP")
    public void deleteAgeGroup(Long id) {
        if (!ageGroupRepository.existsById(id)) {
            throw new AppException(ErrorCode.AGE_GROUP_NOT_FOUND);
        }
        ageGroupRepository.deleteById(id);
    }
}
