package com.kindergarten.warehouse.dto.request;

import lombok.Data;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
