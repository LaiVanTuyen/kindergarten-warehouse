package com.kindergarten.warehouse.service.impl;

import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.repository.CategoryRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.service.TopicService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicServiceImpl implements TopicService {

    private final TopicRepository topicRepository;
    private final CategoryRepository categoryRepository;
    private final MessageSource messageSource;

    public TopicServiceImpl(TopicRepository topicRepository, CategoryRepository categoryRepository,
            MessageSource messageSource) {
        this.topicRepository = topicRepository;
        this.categoryRepository = categoryRepository;
        this.messageSource = messageSource;
    }

    @Override
    public List<Topic> getAllTopics() {
        return topicRepository.findAll();
    }

    @Override
    public Topic createTopic(Topic topic, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException(
                        messageSource.getMessage("error.category.not_found", null, LocaleContextHolder.getLocale())));
        topic.setCategory(category);
        return topicRepository.save(topic);
    }

    @Override
    public Topic updateTopic(Long id, Topic topicDetails) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        messageSource.getMessage("error.topic.not_found", null, LocaleContextHolder.getLocale())));

        topic.setName(topicDetails.getName());
        topic.setDescription(topicDetails.getDescription());

        return topicRepository.save(topic);
    }

    @Override
    public void deleteTopic(Long id) {
        topicRepository.deleteById(id);
    }
}
