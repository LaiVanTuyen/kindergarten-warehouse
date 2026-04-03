package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.TopicRequest;
import com.kindergarten.warehouse.dto.response.TopicResponse;
import java.util.List;

import com.kindergarten.warehouse.dto.wrapper.UpdateResult;

public interface TopicService {
        org.springframework.data.domain.Page<TopicResponse> getAllTopics(Long categoryId, boolean deleted,
                        String keyword, org.springframework.data.domain.Pageable pageable);

        TopicResponse createTopic(TopicRequest topicRequest, Long categoryId);

        UpdateResult<TopicResponse> updateTopic(Long id, TopicRequest topicRequest);

        void deleteTopic(Long id, boolean hard);

        TopicResponse restoreTopic(Long id);
}
