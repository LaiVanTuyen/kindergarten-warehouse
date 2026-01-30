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

        CategoryResponse.CategoryResponseBuilder builder = CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .icon(category.getIcon())
                .description(category.getDescription())
                .topicCount(category.getTopicCount())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt());

        if (category.getCreator() != null) {
            builder.createdBy(category.getCreator().getFullName());
        }

        if (category.getUpdater() != null) {
            builder.updatedBy(category.getUpdater().getFullName());
        }

        return builder.build();
    }
}
