package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.TopicRequest;
import com.kindergarten.warehouse.dto.response.TopicResponse;
import java.util.List;

public interface TopicService {
        List<TopicResponse> getAllTopics(Long categoryId);

        TopicResponse createTopic(TopicRequest topicRequest, Long categoryId);

        TopicResponse updateTopic(Long id, TopicRequest topicRequest);

        void deleteTopic(Long id);
}
