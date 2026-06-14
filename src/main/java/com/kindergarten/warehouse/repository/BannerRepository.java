package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

@Repository
public interface BannerRepository extends JpaRepository<Banner, Long> {

    @Query("SELECT b FROM Banner b " +
            "LEFT JOIN FETCH b.creator " +
            "LEFT JOIN FETCH b.updater " +
            "WHERE b.visibility = com.kindergarten.warehouse.entity.Visibility.PUBLIC " +
            "AND b.isDeleted = false " +
            "AND (:platform IS NULL OR b.platform = :platform) " +
            "AND (b.startDate IS NULL OR b.startDate <= :now) " +
            "AND (b.endDate IS NULL OR b.endDate >= :now) " +
            "ORDER BY b.displayOrder ASC")
    List<Banner> findActiveBanners(@Param("platform") String platform, @Param("now") LocalDateTime now);

    @Query("SELECT b FROM Banner b WHERE b.isDeleted = false AND (:platform IS NULL OR b.platform = :platform)")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
    Page<Banner> findAllByIsDeletedFalseAndPlatform(@Param("platform") String platform, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Banner b SET b.displayOrder = b.displayOrder + 1 WHERE b.isDeleted = false AND (b.platform = :platform OR (:platform IS NULL AND b.platform IS NULL))")
    void incrementDisplayOrderForPlatform(@Param("platform") String platform);
}
