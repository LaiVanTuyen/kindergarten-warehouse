package com.kindergarten.warehouse.dto.response;

import com.kindergarten.warehouse.entity.ReportReason;
import com.kindergarten.warehouse.entity.ReportStatus;
import com.kindergarten.warehouse.entity.ResourceReport;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        String resourceId,
        String resourceTitle,
        String resourceSlug,
        Long reporterId,
        String reporterName,
        ReportReason reason,
        String details,
        ReportStatus status,
        String resolutionNote,
        String resolvedByName,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt) {

    public static ReportResponse from(ResourceReport r) {
        return new ReportResponse(
                r.getId(),
                r.getResource().getId(),
                r.getResource().getTitle(),
                r.getResource().getSlug(),
                r.getReporter().getId(),
                displayName(r.getReporter()),
                r.getReason(),
                r.getDetails(),
                r.getStatus(),
                r.getResolutionNote(),
                r.getResolvedBy() == null ? null : displayName(r.getResolvedBy()),
                r.getResolvedAt(),
                r.getCreatedAt());
    }

    private static String displayName(com.kindergarten.warehouse.entity.User user) {
        return user.getFullName() != null ? user.getFullName() : user.getUsername();
    }
}
