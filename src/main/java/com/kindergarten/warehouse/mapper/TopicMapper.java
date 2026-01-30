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

        TopicResponse.TopicResponseBuilder builder = TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .slug(topic.getSlug())
                .description(topic.getDescription())
                .resourceCount(topic.getResourceCount())
                .isActive(topic.getIsActive())
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt());

        if (topic.getCategory() != null) {
            builder.categoryId(topic.getCategory().getId())
                    .categoryName(topic.getCategory().getName());
        }

        if (topic.getCreator() != null) {
            builder.createdBy(topic.getCreator().getFullName());
        }

        if (topic.getUpdater() != null) {
            builder.updatedBy(topic.getUpdater().getFullName());
        }

        return builder.build();
    }
}
