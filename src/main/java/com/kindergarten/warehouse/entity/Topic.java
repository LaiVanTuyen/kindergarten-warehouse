package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "topics", indexes = {
    @Index(name = "idx_topic_name", columnList = "name"),
    @Index(name = "idx_topic_is_deleted", columnList = "is_deleted"),
    @Index(name = "idx_topic_category", columnList = "category_id")
})
@EqualsAndHashCode(callSuper = false)
public class Topic extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    @NotBlank
    private String name;

    @Column(unique = true, length = 150)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @ToString.Exclude
    private Category category;

    @org.hibernate.annotations.Formula("(SELECT COUNT(*) FROM resources r WHERE r.topic_id = id AND r.is_deleted = false)")
    private Long resourceCount;

    @Builder.Default
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

}
