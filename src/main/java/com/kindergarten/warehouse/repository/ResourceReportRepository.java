package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.ReportStatus;
import com.kindergarten.warehouse.entity.ResourceReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResourceReportRepository extends JpaRepository<ResourceReport, Long> {

    boolean existsByResourceIdAndReporterIdAndStatus(String resourceId, Long reporterId, ReportStatus status);

    @EntityGraph(attributePaths = {"resource", "reporter", "resolvedBy"})
    @Query(value = "SELECT r FROM ResourceReport r WHERE (:status IS NULL OR r.status = :status)",
            countQuery = "SELECT COUNT(r) FROM ResourceReport r WHERE (:status IS NULL OR r.status = :status)")
    Page<ResourceReport> search(@Param("status") ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"resource", "reporter", "resolvedBy"})
    @Query("SELECT r FROM ResourceReport r WHERE r.id = :id")
    Optional<ResourceReport> findByIdWithDetails(@Param("id") Long id);

    /** Row lock without fetch joins so only the report row is locked; associations load lazily. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResourceReport r WHERE r.id = :id")
    Optional<ResourceReport> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(ReportStatus status);
}
