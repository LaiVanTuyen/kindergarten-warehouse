package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.repository.CategoryRepository;
import com.kindergarten.warehouse.dto.request.TopicRequest;
import com.kindergarten.warehouse.dto.response.TopicResponse;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.service.TopicService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final CategoryRepository categoryRepository;

    public TopicServiceImpl(TopicRepository topicRepository, CategoryRepository categoryRepository) {
        this.topicRepository = topicRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<TopicResponse> getAllTopics() {
        return topicRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public TopicResponse createTopic(TopicRequest topicRequest, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.CATEGORY_NOT_FOUND));

        Topic topic = new Topic();
        topic.setName(topicRequest.getName());
        topic.setDescription(topicRequest.getDescription());
        topic.setCategory(category);

        return mapToResponse(topicRepository.save(topic));
    }

    @Override
    public TopicResponse updateTopic(Long id, TopicRequest topicRequest) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new com.kindergarten.warehouse.exception.AppException(
                        com.kindergarten.warehouse.exception.ErrorCode.TOPIC_NOT_FOUND));

        topic.setName(topicRequest.getName());
        topic.setDescription(topicRequest.getDescription());

        return mapToResponse(topicRepository.save(topic));
    }

    @Override
    public void deleteTopic(Long id) {
        if (!topicRepository.existsById(id)) {
            throw new com.kindergarten.warehouse.exception.AppException(
                    com.kindergarten.warehouse.exception.ErrorCode.TOPIC_NOT_FOUND);
        }
        topicRepository.deleteById(id);
    }

    private TopicResponse mapToResponse(Topic topic) {
        return TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .categoryId(topic.getCategory().getId())
                .categoryName(topic.getCategory().getName())
                .build();
    }
}
