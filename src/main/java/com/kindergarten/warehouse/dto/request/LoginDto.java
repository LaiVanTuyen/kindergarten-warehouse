package com.kindergarten.warehouse.dto.request;

import lombok.Data;

@Data
public class LoginDto {
    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    private String username;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    private String password;
}
