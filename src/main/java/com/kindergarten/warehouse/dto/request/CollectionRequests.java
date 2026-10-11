package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.CollectionVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request bodies for {@code /api/v1/collections}. */
public final class CollectionRequests {

    private CollectionRequests() {
    }

    /** Create and full update of a collection's own fields (items are edited separately). */
    public record Upsert(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            Long ageGroupId,
            Long topicId,
            CollectionVisibility visibility,
            /** Required on update (optimistic lock); ignored on create. */
            Long version) {
    }

    public record AddItem(
            @NotBlank String resourceId,
            @Size(max = 1000) String note) {
    }

    public record UpdateItem(@Size(max = 1000) String note) {
    }

    /** Must list exactly the collection's current item ids, in the new order. */
    public record Reorder(@NotNull @NotEmpty List<Long> itemIds) {
    }
}
