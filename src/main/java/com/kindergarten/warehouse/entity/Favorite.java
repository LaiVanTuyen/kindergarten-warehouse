package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "favorites")
@IdClass(Favorite.FavoriteId.class)
public class Favorite {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "resource_id", columnDefinition = "CHAR(36)")
    private String resourceId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoriteId implements Serializable {
        private Long userId;
        private String resourceId;
    }
}
