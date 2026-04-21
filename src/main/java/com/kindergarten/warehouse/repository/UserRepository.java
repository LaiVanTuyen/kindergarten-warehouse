package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameAndIsDeletedFalse(String username);

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    @Query("SELECT u FROM User u WHERE u.isDeleted = false AND (u.username = :identifier OR u.email = :identifier)")
    Optional<User> findActiveByUsernameOrEmail(@Param("identifier") String identifier);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Page<User> findByIsDeletedFalse(Pageable pageable);

    Page<User> findByIsDeletedTrue(Pageable pageable);

    Page<User> findByIsDeletedFalseAndStatus(UserStatus status, Pageable pageable);

    /**
     * Đếm số admin đang active (không bị xóa, không bị khóa) — dùng cho last-admin guard.
     */
    @Query("""
            SELECT COUNT(DISTINCT u)
            FROM User u JOIN u.roles r
            WHERE r = :role
              AND u.isDeleted = false
              AND u.status = com.kindergarten.warehouse.entity.UserStatus.ACTIVE
            """)
    long countActiveUsersByRole(@Param("role") Role role);

    /**
     * Đếm các user ACTIVE khác (ngoại trừ {@code excludeId}) có role tương ứng.
     * Dùng để check "còn admin nào khác ngoài tôi/người đang bị modify không".
     * Đây là cách đúng cho last-admin guard: {@code count==0} nghĩa là target là admin cuối cùng.
     */
    @Query("""
            SELECT COUNT(DISTINCT u)
            FROM User u JOIN u.roles r
            WHERE r = :role
              AND u.id <> :excludeId
              AND u.isDeleted = false
              AND u.status = com.kindergarten.warehouse.entity.UserStatus.ACTIVE
            """)
    long countActiveUsersByRoleExcluding(@Param("role") Role role, @Param("excludeId") Long excludeId);

    @Modifying
    @Query("UPDATE User u SET u.lastActive = :lastActive WHERE u.id IN :ids")
    void batchUpdateLastActive(@Param("ids") List<Long> ids, @Param("lastActive") LocalDateTime lastActive);
}
