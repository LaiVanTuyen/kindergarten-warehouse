package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class UserCreationRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @Email(message = "Email invalid")
    @NotBlank(message = "Email is required")
    private String email;

    private String fullName;

    // Optional: Assign roles upon creation (if admin allows)
    private Set<String> roles;
}
