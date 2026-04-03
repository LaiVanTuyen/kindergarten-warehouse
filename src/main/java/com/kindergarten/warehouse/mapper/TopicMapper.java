package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.TopicResponse;
import com.kindergarten.warehouse.entity.Topic;
import org.springframework.stereotype.Component;

@Component
public class TopicMapper {

    public TopicResponse toResponse(Topic topic) {
        if (topic == null) {
            return null;
        }

        return TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .slug(topic.getSlug())
                .description(topic.getDescription())
                .resourceCount(topic.getResourceCount())
                .isActive(topic.getIsActive())
                .categoryId(topic.getCategory() != null ? topic.getCategory().getId() : null)
                .categoryName(topic.getCategory() != null ? topic.getCategory().getName() : null)
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt())
                .createdBy(topic.getCreator() != null ? topic.getCreator().getFullName() : null)
                .updatedBy(topic.getUpdater() != null ? topic.getUpdater().getFullName() : null)
                .build();
    }
}
