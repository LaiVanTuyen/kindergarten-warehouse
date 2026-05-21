package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.dto.response.ResourceResponse;
import com.kindergarten.warehouse.entity.AgeGroup;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.ResourceMapper;
import com.kindergarten.warehouse.repository.AgeGroupRepository;
import com.kindergarten.warehouse.repository.FavoriteRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hẹp transaction boundary cho luồng upload/update resource: chỉ giữ DB connection
 * khi đọc/ghi entity, bỏ ra ngoài transaction các call I/O chậm như upload MinIO
 * và verify YouTube. Mapping sang DTO cũng nằm trong transaction để tránh
 * LazyInitializationException khi OSIV đã disable.
 */
@Component
@RequiredArgsConstructor
public class ResourceTxHelper {

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final ResourceRepository resourceRepository;
    private final FavoriteRepository favoriteRepository;
    private final ResourceMapper resourceMapper;

    @Transactional(readOnly = true)
    public User getUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Topic getTopicOrThrow(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Set<AgeGroup> loadAgeGroups(Collection<Long> ageGroupIds) {
        if (ageGroupIds == null || ageGroupIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(ageGroupRepository.findAllById(ageGroupIds));
    }

    @Transactional(readOnly = true)
    public Resource findByIdWithDetails(String id) {
        return resourceRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public ResourceResponse saveAndMap(Resource resource, boolean isFavorited) {
        Resource saved = resourceRepository.save(resource);
        return resourceMapper.toResponse(saved, isFavorited);
    }

    @Transactional(readOnly = true)
    public boolean isFavoritedByUser(Long userId, String resourceId) {
        if (userId == null) return false;
        return favoriteRepository.existsByUserIdAndResourceId(userId, resourceId);
    }

    @Transactional(readOnly = true)
    public List<Resource> findAllResources(List<String> ids) {
        return resourceRepository.findAllById(ids);
    }
}
