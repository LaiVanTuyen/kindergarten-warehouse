package com.kindergarten.warehouse.dto.request;

import com.kindergarten.warehouse.entity.Role;
import lombok.Data;

@Data
public class RegisterDto {
    @jakarta.validation.constraints.NotBlank(message = "Username is required")
    @jakarta.validation.constraints.Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @jakarta.validation.constraints.NotBlank(message = "Password is required")
    @jakarta.validation.constraints.Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @jakarta.validation.constraints.NotBlank(message = "Full name is required")
    private String fullName;

    private Role role; // Optional, default to USER if null
}
