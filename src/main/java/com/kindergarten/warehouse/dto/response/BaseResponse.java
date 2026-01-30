package com.kindergarten.warehouse.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BaseResponse {
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public BaseResponse() {
    }

    public BaseResponse(LocalDateTime createdAt, LocalDateTime updatedAt, String createdBy, String updatedBy) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }
}
