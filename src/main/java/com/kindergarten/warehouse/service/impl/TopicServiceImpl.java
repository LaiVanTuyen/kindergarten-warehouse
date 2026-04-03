package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.aspect.LogAction;
import com.kindergarten.warehouse.dto.request.TopicRequest;
import com.kindergarten.warehouse.dto.response.TopicResponse;
import com.kindergarten.warehouse.dto.wrapper.UpdateResult;
import com.kindergarten.warehouse.entity.AuditAction;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.mapper.TopicMapper;
import com.kindergarten.warehouse.repository.CategoryRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.service.TopicService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final CategoryRepository categoryRepository;
    private final TopicMapper topicMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<TopicResponse> getAllTopics(Long categoryId, boolean deleted, String keyword, Pageable pageable) {
        Specification<Topic> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Deleted Filter
            predicates.add(cb.equal(root.get("isDeleted"), deleted));

            // Category Filter
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            // Keyword Search
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

        // Proactive validation for better UX
        if (topicRepository.existsByNameAndIsDeletedFalse(topicRequest.getName())) {
            throw new AppException(ErrorCode.DUPLICATE_NAME);
        }

        Topic topic = new Topic();
        topic.setName(topicRequest.getName());
        topic.setDescription(topicRequest.getDescription());
        if (topicRequest.getIsActive() != null) {
            topic.setIsActive(topicRequest.getIsActive());
        }
        topic.setCategory(category);

        return topicMapper.toResponse(topicRepository.save(topic));
    }

    @Override
    @LogAction(action = AuditAction.UPDATE, description = "Updated topic", target = "TOPIC")
    public UpdateResult<TopicResponse> updateTopic(Long id, TopicRequest topicRequest) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));

        // Proactive validation: check if name changed and conflicts with existing
        if (!topic.getName().equals(topicRequest.getName())
                && topicRepository.existsByNameAndIsDeletedFalse(topicRequest.getName())) {
            throw new AppException(ErrorCode.DUPLICATE_NAME);
        }

        // Determine message key based on status change
        String messageKey = "topic.update.success";
        if (topicRequest.getIsActive() != null && !topicRequest.getIsActive().equals(topic.getIsActive())) {
            if (topicRequest.getIsActive()) {
                messageKey = "topic.activated";
            } else {
                messageKey = "topic.deactivated";
            }
        }

        topic.setName(topicRequest.getName());
        topic.setDescription(topicRequest.getDescription());
        if (topicRequest.getIsActive() != null) {
            topic.setIsActive(topicRequest.getIsActive());
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
}
