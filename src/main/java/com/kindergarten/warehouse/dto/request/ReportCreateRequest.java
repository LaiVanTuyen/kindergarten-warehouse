package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
        @NotBlank String resourceId,
        @NotNull ReportReason reason,
        @Size(max = 1000) String details) {
}
