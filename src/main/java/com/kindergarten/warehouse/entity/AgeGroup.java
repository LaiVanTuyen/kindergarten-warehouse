package com.kindergarten.warehouse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "age_groups")
@EqualsAndHashCode(callSuper = true)
public class AgeGroup extends BaseEntity {

    @jakarta.persistence.Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(columnDefinition = "TEXT")
    private String description;
}
