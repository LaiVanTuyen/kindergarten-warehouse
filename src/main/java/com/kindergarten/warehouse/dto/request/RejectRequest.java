package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectRequest {
    @NotBlank(message = "Rejection reason cannot be blank")
    @Size(max = 1000, message = "Rejection reason must not exceed 1000 characters")
    private String reason;
}
