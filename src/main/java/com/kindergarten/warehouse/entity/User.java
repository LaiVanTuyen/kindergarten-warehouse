package com.kindergarten.warehouse.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_fullname", columnList = "full_name"),
        @Index(name = "idx_user_phone", columnList = "phone_number"),
        @Index(name = "idx_user_status", columnList = "status")
})
@EqualsAndHashCode(callSuper = false, of = "id")
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

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    /** Snapshot gốc của username tại thời điểm xóa mềm, giúp restore chính xác. */
    @Column(name = "original_username", length = 50)
    private String originalUsername;

    /** Snapshot gốc của email tại thời điểm xóa mềm, giúp restore chính xác. */
    @Column(name = "original_email", length = 100)
    private String originalEmail;

    /**
     * Tăng mỗi khi cần vô hiệu hóa toàn bộ JWT đã phát hành cho user này
     * (đổi mật khẩu, block, reset password, logout all devices).
     */
    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Long tokenVersion = 0L;

    /**
     * JPA optimistic locking: Hibernate tự tăng mỗi lần update và ném
     * {@link jakarta.persistence.OptimisticLockException} nếu 2 transaction
     * cùng sửa 1 row.
     */
    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }

    public boolean isLoginAllowed() {
        return Boolean.FALSE.equals(isDeleted) && status == UserStatus.ACTIVE;
    }

    public void incrementTokenVersion() {
        this.tokenVersion = (this.tokenVersion == null ? 0L : this.tokenVersion) + 1L;
    }
}
