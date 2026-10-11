package com.kindergarten.warehouse.dto.response;

import java.util.List;

/** Response bodies for {@code /api/v1/ai}. */
public final class AiResponses {

    private AiResponses() {
    }

    public record Status(boolean enabled, int dailyLimit, int remainingToday) {
    }

    /** A real warehouse resource the viewer can open, with the model's one-line reason. */
    public record SearchItem(ResourceResponse resource, String reason) {
    }

    public record SearchResult(List<SearchItem> items, int remainingToday) {
    }

    /**
     * {@code resourceIds} only ever contains ids the teacher may open (or, for an adjustment,
     * ids already in their plan). {@code resources} resolves the openable ones for display.
     */
    public record DraftStep(
            String title,
            Integer durationMinutes,
            String description,
            List<String> resourceIds,
            List<ResourceResponse> resources) {
    }

    /** Never saved by the server: the teacher reviews it, then creates/saves the plan. */
    public record PlanDraft(
            String title,
            Integer durationMinutes,
            String objectives,
            String preparation,
            String adaptations,
            String notes,
            List<DraftStep> steps,
            int remainingToday) {
    }
}
