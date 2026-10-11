package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.CollectionVisibility;

import java.time.LocalDateTime;
import java.util.List;

/** Response bodies for {@code /api/v1/collections}. */
public final class CollectionResponses {

    private CollectionResponses() {
    }

    public record Ref(Long id, String name) {
    }

    public record Summary(
            Long id,
            String title,
            String description,
            CollectionVisibility visibility,
            Ref ageGroup,
            Ref topic,
            Long ownerId,
            String ownerName,
            boolean isOwner,
            long itemCount,
            Long sourceCollectionId,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /**
     * {@code resource} is null when the viewer may not see it any more (deleted,
     * taken down, made private). Its title is deliberately not exposed then.
     */
    public record Item(
            Long id,
            int position,
            String note,
            String resourceId,
            boolean available,
            ResourceResponse resource) {
    }

    public record Detail(Summary collection, List<Item> items) {
    }

    /** For the "add to collection" dialog on a resource page. */
    public record Membership(Long id, String title, long itemCount, boolean containsResource) {
    }
}
