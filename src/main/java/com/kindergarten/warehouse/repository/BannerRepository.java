package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Banner> {

    @Query("SELECT b FROM Banner b WHERE b.isDeleted = false AND (:platform IS NULL OR b.platform = :platform)")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
    Page<Banner> findAllByIsDeletedFalseAndPlatform(@Param("platform") String platform, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Banner b SET b.displayOrder = b.displayOrder + 1 WHERE b.isDeleted = false AND (b.platform = :platform OR (:platform IS NULL AND b.platform IS NULL))")
    void incrementDisplayOrderForPlatform(@Param("platform") String platform);
}
