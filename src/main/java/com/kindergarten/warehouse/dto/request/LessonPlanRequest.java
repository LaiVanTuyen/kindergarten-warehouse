package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.ContentSource;
import com.kindergarten.warehouse.entity.LessonPlanStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * The whole plan document. Create and update both send everything: steps are
 * replaced as a unit, which keeps "reorder/add/remove step" a single atomic save.
 */
public record LessonPlanRequest(
        @NotBlank @Size(max = 200) String title,
        Long ageGroupId,
        Long topicId,
        @Min(1) @Max(240) Integer durationMinutes,
        @Min(1) @Max(100) Integer classSize,
        @Size(max = 4000) String objectives,
        @Size(max = 4000) String preparation,
        @Size(max = 4000) String adaptations,
        @Size(max = 4000) String reflection,
        LocalDate taughtOn,
        LessonPlanStatus status,
        /** Only honoured for ADMIN; ignored otherwise. */
        Boolean template,
        Long collectionId,
        @Valid @Size(max = 20) List<@jakarta.validation.constraints.NotNull Step> steps,
        /** Required on update (optimistic lock); ignored on create. */
        Long version) {

    public record Step(
            @NotBlank @Size(max = 200) String title,
            @Min(0) @Max(240) Integer durationMinutes,
            @Size(max = 4000) String description,
            /** Defaults to TEACHER. Phase 2 sends AI_SUGGESTED for unedited AI drafts. */
            ContentSource source,
            @Size(max = 10) List<String> resourceIds) {
    }
}
