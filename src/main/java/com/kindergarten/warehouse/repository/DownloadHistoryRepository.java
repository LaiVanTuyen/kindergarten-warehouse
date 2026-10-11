package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.DownloadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloadHistoryRepository
        extends JpaRepository<DownloadHistory, DownloadHistory.DownloadHistoryId>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<DownloadHistory> {

    /** Upsert in one statement so concurrent downloads never collide on the PK. */
    @Modifying
    @Query(value = "INSERT INTO download_history (user_id, resource_id, download_count, last_downloaded_at) "
            + "VALUES (:userId, :resourceId, 1, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE download_count = download_count + 1, "
            + "last_downloaded_at = CURRENT_TIMESTAMP", nativeQuery = true)
    void recordDownload(@Param("userId") Long userId, @Param("resourceId") String resourceId);
}
