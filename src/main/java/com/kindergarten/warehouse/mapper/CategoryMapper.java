package com.kindergarten.warehouse.mapper;

import com.kindergarten.warehouse.dto.response.CategoryResponse;
import com.kindergarten.warehouse.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        if (category == null) {
            return null;
        }

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .icon(category.getIcon())
                .description(category.getDescription())
                .topicCount(category.getTopicCount())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .createdBy(category.getCreator() != null ? category.getCreator().getFullName() : null)
                .updatedBy(category.getUpdater() != null ? category.getUpdater().getFullName() : null)
                .build();
    }
}
