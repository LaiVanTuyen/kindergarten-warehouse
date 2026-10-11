package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "download_history")
@IdClass(DownloadHistory.DownloadHistoryId.class)
public class DownloadHistory {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "resource_id", columnDefinition = "CHAR(36)")
    private String resourceId;

    /** Read-only; lets history queries apply Portal visibility in SQL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", insertable = false, updatable = false)
    @lombok.ToString.Exclude
    private Resource resource;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount;

    @Column(name = "last_downloaded_at", nullable = false)
    private LocalDateTime lastDownloadedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DownloadHistoryId implements Serializable {
        private Long userId;
        private String resourceId;
    }
}
