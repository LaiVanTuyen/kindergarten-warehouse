package com.kindergarten.warehouse.dto.response;

import java.time.LocalDateTime;

/** Public author page. Deliberately excludes email, phone and username. */
public record AuthorProfileResponse(
        Long id,
        String fullName,
        String avatarUrl,
        String bio,
        LocalDateTime joinedAt,
        long resourceCount,
        long totalViews,
        long totalDownloads) {
}
