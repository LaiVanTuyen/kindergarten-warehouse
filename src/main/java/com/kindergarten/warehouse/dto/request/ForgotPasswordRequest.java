package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "{validation.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;
}
