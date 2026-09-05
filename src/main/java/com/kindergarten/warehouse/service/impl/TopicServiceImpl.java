package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.request.TopicRequest;
import com.kindergarten.warehouse.dto.response.TopicResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.entity.AuditAction;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.TopicMapper;
import com.kindergarten.warehouse.repository.CategoryRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.service.TopicService;
import com.kindergarten.warehouse.util.SlugUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final CategoryRepository categoryRepository;
    private final TopicMapper topicMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<TopicResponse> getAllTopics(Long categoryId, boolean deleted, String keyword, Pageable pageable,
            com.kindergarten.warehouse.security.Viewer viewer) {
        Specification<Topic> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("isDeleted"), viewer.isAdmin() && deleted));
            if (!viewer.isAdmin()) {
                predicates.add(cb.isFalse(root.get("category").get("isDeleted")));
                if (viewer.isAuthenticated()) {
                    predicates.add(root.get("visibility").in(Visibility.PUBLIC, Visibility.INTERNAL));
                    predicates.add(root.get("category").get("visibility")
                            .in(Visibility.PUBLIC, Visibility.INTERNAL));
                } else {
                    predicates.add(cb.equal(root.get("visibility"), Visibility.PUBLIC));
                    predicates.add(cb.equal(root.get("category").get("visibility"), Visibility.PUBLIC));
                }
            }

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            if (keyword != null && !keyword.isEmpty()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), likePattern),
                        cb.like(cb.lower(root.get("description")), likePattern)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return topicRepository.findAll(spec, pageable).map(topicMapper::toResponse);
    }

    @Override
    @LogAction(action = AuditAction.CREATE, description = "Created topic", target = "TOPIC")
    public TopicResponse createTopic(TopicRequest topicRequest, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        if (topicRepository.existsByNameAndIsDeletedFalse(topicRequest.getName())) {
            throw new AppException(ErrorCode.DUPLICATE_NAME);
        }

        Topic topic = new Topic();
        topic.setName(topicRequest.getName());
        topic.setSlug(resolveCreateSlug(topicRequest));
        topic.setDescription(topicRequest.getDescription());
        if (topicRequest.getVisibility() != null) {
            topic.setVisibility(topicRequest.getVisibility());
        }
        topic.setCategory(category);

        return topicMapper.toResponse(topicRepository.save(topic));
    }

    @Override
    @LogAction(action = AuditAction.UPDATE, description = "Updated topic", target = "TOPIC")
    public UpdateResult<TopicResponse> updateTopic(Long id, TopicRequest topicRequest) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));

        if (!topic.getName().equals(topicRequest.getName())
                && topicRepository.existsByNameAndIsDeletedFalse(topicRequest.getName())) {
            throw new AppException(ErrorCode.DUPLICATE_NAME);
        }

        String slug = resolveSlug(topicRequest);
        if (!Objects.equals(topic.getSlug(), slug) && topicRepository.existsBySlugAndIsDeletedFalse(slug)) {
            throw new AppException(ErrorCode.DUPLICATE_SLUG);
        }

        String messageKey = "topic.update.success";
        if (topicRequest.getVisibility() != null
                && topicRequest.getVisibility() != topic.getVisibility()) {
            messageKey = topicRequest.getVisibility() == Visibility.PUBLIC
                    ? "topic.activated"
                    : "topic.deactivated";
        }

        topic.setName(topicRequest.getName());
        topic.setSlug(slug);
        topic.setDescription(topicRequest.getDescription());
        if (topicRequest.getVisibility() != null) {
            topic.setVisibility(topicRequest.getVisibility());
        }

        return new UpdateResult<>(
                topicMapper.toResponse(topicRepository.save(topic)),
                messageKey);
    }

    @Override
    @LogAction(action = AuditAction.DELETE, description = "Deleted topic", target = "TOPIC")
    public void deleteTopic(Long id, boolean hard) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));

        if (hard) {
            topicRepository.delete(topic);
        } else {
            topic.setIsDeleted(true);
            topicRepository.save(topic);
        }
    }

    @Override
    @LogAction(action = AuditAction.RESTORE, description = "Restored topic", target = "TOPIC")
    public TopicResponse restoreTopic(Long id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));

        topic.setIsDeleted(false);
        return topicMapper.toResponse(topicRepository.save(topic));
    }

    private String resolveCreateSlug(TopicRequest topicRequest) {
        String slug = resolveSlug(topicRequest);
        if (topicRepository.existsBySlugAndIsDeletedFalse(slug)) {
            if (StringUtils.hasText(topicRequest.getSlug())) {
                throw new AppException(ErrorCode.DUPLICATE_SLUG);
            }
            slug = buildUniqueGeneratedSlug(slug);
        }
        return slug;
    }

    private String resolveSlug(TopicRequest topicRequest) {
        String source = StringUtils.hasText(topicRequest.getSlug()) ? topicRequest.getSlug() : topicRequest.getName();
        String slug = SlugUtil.toSlug(source);
        if (!StringUtils.hasText(slug)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        return slug;
    }

    private String buildUniqueGeneratedSlug(String baseSlug) {
        String slug = baseSlug;
        int suffix = 2;
        while (topicRepository.existsBySlugAndIsDeletedFalse(slug)) {
            slug = baseSlug + "-" + suffix;
            suffix++;
        }
        return slug;
    }
}
