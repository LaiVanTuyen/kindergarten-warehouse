package com.kindergarten.warehouse.dto.request;

import lombok.Data;

@Data
public class RegisterDto {
    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    @jakarta.validation.constraints.Size(min = 3, max = 50, message = "{validation.size}")
    private String username;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    @jakarta.validation.constraints.Size(min = 6, message = "{validation.size}")
    private String password;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    @jakarta.validation.constraints.Email(message = "{validation.email.invalid}")
    private String email;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    private String fullName;
}
