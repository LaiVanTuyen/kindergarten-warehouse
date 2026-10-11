package com.kindergarten.warehouse.service.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured-output schemas the model must fill. The SDK derives the JSON schema from
 * these records, so Claude's reply is parsed into them directly (no free-text parsing).
 * Everything here is still validated by {@link AiAssistantService} before use.
 */
public final class AiSchemas {

    private AiSchemas() {
    }

    public record SearchIntent(
            @JsonPropertyDescription("2-5 short Vietnamese search phrases for the warehouse (title/description/tag), most specific first")
            List<String> keywords,
            @JsonPropertyDescription("Age-group slugs from the provided list that the teacher asked for; empty if not mentioned")
            List<String> ageGroupSlugs,
            @JsonPropertyDescription("File types from the provided list the teacher asked for (e.g. VIDEO for 'có video'); empty if not mentioned")
            List<String> fileTypes) {
    }

    public record Pick(
            @JsonPropertyDescription("id of a resource from the provided candidate list, copied exactly")
            String resourceId,
            @JsonPropertyDescription("One short Vietnamese sentence: why this resource fits the request")
            String reason) {
    }

    public record Ranking(
            @JsonPropertyDescription("Best matches first, at most 8. Empty when no candidate fits.")
            List<Pick> picks) {
    }

    public record DraftStep(
            @JsonPropertyDescription("Step name, e.g. Khởi động, Khám phá, Trò chơi, Kết thúc")
            String title,
            @JsonPropertyDescription("Minutes for this step")
            Integer durationMinutes,
            @JsonPropertyDescription("What the teacher does and what children do, guiding questions; Vietnamese, plain text with line breaks")
            String description,
            @JsonPropertyDescription("ids of candidate resources used in this step, copied exactly; empty if none")
            List<String> resourceIds) {
    }

    public record PlanDraft(
            @JsonPropertyDescription("Plan title in Vietnamese")
            String title,
            @JsonPropertyDescription("Total minutes")
            Integer durationMinutes,
            @JsonPropertyDescription("Learning objectives, one per line")
            String objectives,
            @JsonPropertyDescription("Materials and equipment to prepare, one per line; only what the teacher has or can easily make")
            String preparation,
            @JsonPropertyDescription("Adaptations: support for children who need more guidance, fallbacks (e.g. no projector)")
            String adaptations,
            List<DraftStep> steps,
            @JsonPropertyDescription("Short note to the teacher about what you assumed or what to double-check; Vietnamese")
            String notes) {
    }
}
