package com.kindergarten.warehouse.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for {@code /api/v1/ai}. */
public final class AiRequests {

    private AiRequests() {
    }

    /** "Tìm bài hát về gia đình cho trẻ 3–4 tuổi, có video." */
    public record Search(@NotBlank @Size(max = 500) String query) {
    }

    /** "Soạn hoạt động 20 phút về màu sắc, lớp có 25 trẻ, chỉ có giấy và bút màu." */
    public record Draft(
            @NotBlank @Size(max = 1000) String request,
            Long ageGroupId,
            @Min(1) @Max(240) Integer durationMinutes,
            @Min(1) @Max(100) Integer classSize) {
    }

    /**
     * "Rút xuống 15 phút." Works on the plan as currently shown in the editor (possibly
     * unsaved), so nothing the teacher typed is lost; the result is again only a draft.
     */
    public record Adjust(
            @NotBlank @Size(max = 500) String instruction,
            @NotNull @Valid LessonPlanRequest plan) {
    }
}
