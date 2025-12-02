package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "categories")
@EqualsAndHashCode(callSuper = false)
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

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<Topic> topics;
}
