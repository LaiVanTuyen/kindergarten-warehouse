package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body bulk dùng chung cho các entity có khóa Long (category/topic) — field `ids`
 * theo API_CONTRACT_V1 §2.5.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkLongIdsRequest {

    @NotEmpty(message = "{validation.required}")
    @Size(min = 1, max = 1000, message = "{validation.size}")
    private List<Long> ids;
}
