package com.kindergarten.warehouse.dto.request;

import lombok.Data;

@Data
public class LoginDto {
    @jakarta.validation.constraints.NotBlank(message = "Username is required")
    private String username;

    @jakarta.validation.constraints.NotBlank(message = "Password is required")
    private String password;
}
