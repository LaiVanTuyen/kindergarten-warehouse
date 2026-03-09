package com.kindergarten.warehouse.scheduler;

import com.kindergarten.warehouse.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogCleanupScheduler {

    private final AuditLogRepository auditLogRepository;

    /**
     * Runs automatically at 02:00 AM on the 1st day of every month.
     * Deletes all Audit Logs that are older than 6 months to free up persistent DB
     * storage.
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void cleanupOldAuditLogs() {
        log.info("Starting scheduled background cleanup of old Audit Logs (Data Retention)...");

        // Threshold: 6 Months ago
        LocalDateTime thresholdDate = LocalDateTime.now().minusMonths(6);

        try {
            int totalDeleted = 0;
            int deletedCount;
            do {
                deletedCount = auditLogRepository.deleteByTimestampBeforeChunked(thresholdDate);
                totalDeleted += deletedCount;
            } while (deletedCount > 0);

            log.info("Successfully purged {} old Audit Logs (older than {}).", totalDeleted, thresholdDate);
        } catch (Exception e) {
            log.error("Failed to run Audit Log Cleanup Scheduler: {}", e.getMessage());
        }
    }
}
