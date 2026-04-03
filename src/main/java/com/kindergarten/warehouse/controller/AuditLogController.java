package com.kindergarten.warehouse.controller;

import com.kindergarten.warehouse.dto.request.AuditLogFilterRequest;
import com.kindergarten.warehouse.dto.response.ApiResponse;
import com.kindergarten.warehouse.entity.AuditLog;
import com.kindergarten.warehouse.service.AuditLogService;
import com.kindergarten.warehouse.service.MessageService;
import com.kindergarten.warehouse.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

        private final AuditLogService auditLogService;
        private final MessageService messageService;

        @GetMapping
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
                        @ModelAttribute AuditLogFilterRequest filterRequest,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        @RequestParam(defaultValue = "timestamp") String sortBy,
                        @RequestParam(defaultValue = "desc") String sortDir) {

                Pageable pageable = PageableUtils.createPageable(page, size, sortBy, sortDir);

                Page<AuditLog> logs = auditLogService.getLogs(filterRequest, pageable);

                return ResponseEntity.ok(ApiResponse.success(logs, "Audit logs retrieved successfully"));
        }

        @GetMapping("/export")
        @PreAuthorize("hasAuthority('ADMIN')")
        public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportAuditLogs(
                        @ModelAttribute AuditLogFilterRequest filterRequest,
                        @RequestParam(defaultValue = "timestamp") String sortBy,
                        @RequestParam(defaultValue = "desc") String sortDir) {

                org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.fromString(sortDir), sortBy);

                org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody stream = out -> {
                        auditLogService.exportLogsToStream(filterRequest, sort, out);
                };

                return ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"audit_logs.csv\"")
                                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                                .body(stream);
        }
}
