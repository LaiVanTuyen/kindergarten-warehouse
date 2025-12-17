package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.AuditLog;
import com.kindergarten.warehouse.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void saveLog(String action, String username, String target, String detail) {
        AuditLog log = AuditLog.builder()
                .action(action)
                .username(username)
                .target(target)
                .detail(detail)
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(log);
    }

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }

    public List<AuditLog> getLogsByUsername(String username) {
        return auditLogRepository.findByUsername(username);
    }
}
