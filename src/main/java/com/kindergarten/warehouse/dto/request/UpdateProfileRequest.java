package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProfileRequest {
    @Size(min = 3, message = "FULL_NAME_INVALID")
    String fullName;

    String phoneNumber;

    String bio;
}
