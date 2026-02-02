package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmResetPasswordRequest {
    @NotBlank(message = "OTP is required")
    private String otp;
}
