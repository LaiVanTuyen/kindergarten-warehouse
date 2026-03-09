package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    List<AuditLog> findByUsername(String username);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM audit_log WHERE timestamp < :thresholdDate LIMIT 1000", nativeQuery = true)
    int deleteByTimestampBeforeChunked(
            @org.springframework.data.repository.query.Param("thresholdDate") LocalDateTime thresholdDate);
}
