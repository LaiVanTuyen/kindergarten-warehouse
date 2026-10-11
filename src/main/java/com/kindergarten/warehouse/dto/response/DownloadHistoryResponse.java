package com.kindergarten.warehouse.dto.response;

import java.time.LocalDateTime;

public record DownloadHistoryResponse(
        ResourceResponse resource,
        int downloadCount,
        LocalDateTime lastDownloadedAt) {
}
