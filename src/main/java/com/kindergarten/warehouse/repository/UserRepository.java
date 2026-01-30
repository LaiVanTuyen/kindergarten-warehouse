package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository
        extends JpaRepository<User, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndIsDeletedFalse(String username);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    org.springframework.data.domain.Page<User> findByIsDeletedFalse(org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<User> findByIsDeletedTrue(org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<User> findByIsDeletedFalseAndStatus(
            com.kindergarten.warehouse.entity.UserStatus status, org.springframework.data.domain.Pageable pageable);

    Optional<User> findByEmail(String email);
}
