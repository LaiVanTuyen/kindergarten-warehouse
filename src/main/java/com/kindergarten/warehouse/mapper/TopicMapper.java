package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.TopicResponse;
import com.kindergarten.warehouse.entity.Topic;
import org.springframework.stereotype.Component;

@Component
public class TopicMapper {

    /**
     * Map từ projection của danh sách Portal.
     *
     * <p>Không có {@code createdBy}/{@code updatedBy} vì projection cố ý không
     * join bảng {@code users} — Portal không hiển thị hai trường này.
     *
     * <p>{@code resourceCount} truyền vào từ batch query gom nhóm cho cả trang,
     * không phải subquery tương quan như {@code @Formula} cũ.
     */
    public TopicResponse toResponse(
            com.kindergarten.warehouse.repository.projection.ResourceListView.TopicView topic,
            Long resourceCount) {
        if (topic == null) {
            return null;
        }

        return TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .slug(topic.getSlug())
                .description(topic.getDescription())
                .resourceCount(resourceCount)
                .visibility(topic.getVisibility())
                .categoryId(topic.getCategory() != null ? topic.getCategory().getId() : null)
                .categoryName(topic.getCategory() != null ? topic.getCategory().getName() : null)
                .build();
    }

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
                .visibility(topic.getVisibility())
                .categoryId(topic.getCategory() != null ? topic.getCategory().getId() : null)
                .categoryName(topic.getCategory() != null ? topic.getCategory().getName() : null)
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt())
                .createdBy(topic.getCreator() != null ? topic.getCreator().getFullName() : null)
                .updatedBy(topic.getUpdater() != null ? topic.getUpdater().getFullName() : null)
                .build();
    }
}
