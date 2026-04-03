package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {
    @NotBlank(message = "CURRENT_PASSWORD_REQUIRED")
    String currentPassword;

    @NotBlank(message = "NEW_PASSWORD_REQUIRED")
    @Size(min = 8, message = "PASSWORD_INVALID")
    String newPassword;
}
