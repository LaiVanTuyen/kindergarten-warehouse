package com.kindergarten.warehouse.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class PlanCollaborationResponses {

    private PlanCollaborationResponses() {
    }

    public record Comment(Long id, Long authorId, String authorName, String content, LocalDateTime createdAt,
            boolean canDelete) {
    }

    /** Everything the plan page shows under "Phối hợp": usage counter and colleague comments. */
    public record Overview(long teachersUsed, boolean usedByMe, LocalDate myLastUsedOn, List<Comment> comments) {
    }
}
