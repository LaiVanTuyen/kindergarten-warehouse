package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.Visibility;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VisibilityUpdateRequest {
    @NotNull(message = "Visibility is required")
    private Visibility visibility;
}
