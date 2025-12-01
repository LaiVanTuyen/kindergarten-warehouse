package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.Topic;
import java.util.List;

public interface TopicService {
    List<Topic> getAllTopics();

    Topic createTopic(Topic topic, Long categoryId);

    Topic updateTopic(Long id, Topic topicDetails);

    void deleteTopic(Long id);
}
