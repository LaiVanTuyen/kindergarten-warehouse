package com.kindergarten.warehouse.dto.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class LoginDto {
    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    private String email;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    private String password;

}
