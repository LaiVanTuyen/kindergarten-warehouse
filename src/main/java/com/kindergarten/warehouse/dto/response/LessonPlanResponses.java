package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.ContentSource;
import com.kindergarten.warehouse.entity.LessonPlanStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class LessonPlanResponses {

    private LessonPlanResponses() {
    }

    public record Summary(
            Long id,
            String title,
            CollectionResponses.Ref ageGroup,
            CollectionResponses.Ref topic,
            Integer durationMinutes,
            String objectives,
            LessonPlanStatus status,
            boolean template,
            com.kindergarten.warehouse.entity.PlanReviewStatus reviewStatus,
            Long ownerId,
            String ownerName,
            boolean isOwner,
            int stepCount,
            LocalDate taughtOn,
            Long sourcePlanId,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /** {@code resource} is null when this viewer can't open it; the title is then not exposed. */
    public record StepResource(String resourceId, boolean available, ResourceResponse resource) {
    }

    public record Step(
            Long id,
            int position,
            String title,
            Integer durationMinutes,
            String description,
            ContentSource source,
            List<StepResource> resources) {
    }

    public record Detail(
            Summary plan,
            Integer classSize,
            String preparation,
            String adaptations,
            String reflection,
            Long collectionId,
            /** Reviewer's note (approval remarks or requested changes). */
            String reviewNote,
            /** Sum of step durations, to compare with {@code durationMinutes}. */
            int stepMinutesTotal,
            List<Step> steps) {
    }
}
