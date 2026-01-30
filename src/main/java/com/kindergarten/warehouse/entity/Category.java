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

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "categories")
@EqualsAndHashCode(of = "id", callSuper = false)
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    @NotBlank
    private String name;

    @Column(unique = true, nullable = false, length = 150)
    @NotBlank
    private String slug;

    @Column(length = 500)
    private String icon;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Topic> topics;

    @org.hibernate.annotations.Formula("(SELECT COUNT(*) FROM topics t WHERE t.category_id = id AND t.is_deleted = false)")
    private Long topicCount;

    @Builder.Default
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

}
