package com.kindergarten.warehouse.dto.request;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDto {
    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    @jakarta.validation.constraints.Size(min = 3, max = 50, message = "{validation.size}")
    private String username;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    @jakarta.validation.constraints.Size(min = 8, max = 100, message = "{validation.size}")
    @jakarta.validation.constraints.Pattern(regexp = com.kindergarten.warehouse.util.ValidationPatterns.PASSWORD, message = "{validation.password.weak}")
    private String password;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    @jakarta.validation.constraints.Email(message = "{validation.email.invalid}")
    private String email;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    private String fullName;

}
