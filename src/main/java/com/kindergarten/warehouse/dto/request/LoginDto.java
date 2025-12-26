package com.kindergarten.warehouse.dto.request;

import lombok.Data;

public class LoginDto {
    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    private String username;

    @jakarta.validation.constraints.NotBlank(message = "{validation.required}")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
