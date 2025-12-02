package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "banners")
public class Banner extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String link;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "display_order")
    private Integer displayOrder = 0;
}
