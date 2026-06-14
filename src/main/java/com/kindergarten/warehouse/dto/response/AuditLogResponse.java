package com.kindergarten.warehouse.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO trả ra cho audit log. Đóng băng field theo API_CONTRACT_V1 §2.2 để không
 * phơi bày trực tiếp entity {@code AuditLog} ra API.
 */
@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private String action;
    private String username;
    private String target;
    private String detail;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime timestamp;
}
