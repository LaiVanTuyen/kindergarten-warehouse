package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkResourceRequest {

    @NotEmpty(message = "Resource IDs list cannot be empty")
    private List<String> resourceIds;

    // Optional: Used specifically for Bulk Reject
    private String reason;
}
