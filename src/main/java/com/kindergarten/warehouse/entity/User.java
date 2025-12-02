package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    @NotBlank
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    @Email
    @NotBlank
    private String email;

    @Column(nullable = false)
    @NotBlank
    private String password;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    @NotNull
    private Role role;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;
}
