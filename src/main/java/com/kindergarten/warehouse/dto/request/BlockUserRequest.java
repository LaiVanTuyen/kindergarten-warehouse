package com.kindergarten.warehouse.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BlockUserRequest {

    @Size(max = 255, message = "{validation.size}")
    private String reason;
}
