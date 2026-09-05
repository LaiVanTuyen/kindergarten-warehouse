package com.kindergarten.warehouse.repository.projection;

import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.ResourceType;
import com.kindergarten.warehouse.entity.Visibility;

import java.time.LocalDateTime;

/**
 * Narrow projection for Portal list/search responses.
 *
 * <p>Audit relations are deliberately excluded: Portal cards do not display
 * them, and loading them would hydrate sensitive columns from {@code users}.
 * Age groups are fetched once per page by {@code AgeGroupRepository} to keep
 * database pagination intact.</p>
 */
public interface ResourceListView {
    String getId();
    String getTitle();
    String getSlug();
    String getDescription();
    Long getViewsCount();
    String getFileUrl();
    String getThumbnailUrl();
    ResourceType getResourceType();
    String getFileType();
    String getFileExtension();
    Long getFileSize();
    String getDuration();
    ResourceStatus getStatus();
    Long getDownloadCount();
    Double getAverageRating();
    Visibility getVisibility();
    String getRejectionReason();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
    TopicView getTopic();

    interface TopicView {
        Long getId();
        String getName();
        String getSlug();
        String getDescription();
        Visibility getVisibility();
        CategoryView getCategory();
    }

    interface CategoryView {
        Long getId();
        String getName();
    }
}
