package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.ReportCreateRequest;
import com.kindergarten.warehouse.dto.request.ReportResolveRequest;
import com.kindergarten.warehouse.dto.response.ReportResponse;
import com.kindergarten.warehouse.entity.NotificationType;
import com.kindergarten.warehouse.entity.ReportStatus;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceReport;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.ResourceReportRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.ResourceAccessGuard;
import com.kindergarten.warehouse.security.Viewer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** "Báo cáo vi phạm": any signed-in user reports a resource, an admin resolves or dismisses it. */
@Service
@RequiredArgsConstructor
public class ResourceReportService {

    private static final java.util.Map<com.kindergarten.warehouse.entity.ReportReason, String> REASON_LABELS =
            java.util.Map.of(
                    com.kindergarten.warehouse.entity.ReportReason.COPYRIGHT, "vi phạm bản quyền",
                    com.kindergarten.warehouse.entity.ReportReason.INAPPROPRIATE, "nội dung không phù hợp với trẻ",
                    com.kindergarten.warehouse.entity.ReportReason.WRONG_INFO, "thông tin sai lệch",
                    com.kindergarten.warehouse.entity.ReportReason.BROKEN_FILE, "tệp lỗi",
                    com.kindergarten.warehouse.entity.ReportReason.SPAM, "spam/quảng cáo",
                    com.kindergarten.warehouse.entity.ReportReason.OTHER, "lý do khác");

    private final ResourceReportRepository reportRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final ResourceAccessGuard resourceAccessGuard;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    @Transactional
    public ReportResponse create(ReportCreateRequest request, Viewer viewer) {
        // Users can only report what they are allowed to see; otherwise 404 like the detail page.
        Resource resource = resourceAccessGuard.requireViewable(
                resourceRepository.findByIdWithDetails(request.resourceId()).orElse(null), viewer);
        if (reportRepository.existsByResourceIdAndReporterIdAndStatus(
                resource.getId(), viewer.userId(), ReportStatus.OPEN)) {
            throw new AppException(ErrorCode.REPORT_ALREADY_OPEN);
        }
        User reporter = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String details = request.details() == null ? null : request.details().trim();
        try {
            // The exists() check above only gives a friendly error in the common case;
            // uk_resource_reports_open (V25) is what stops two concurrent requests.
            ResourceReport saved = reportRepository.saveAndFlush(ResourceReport.builder()
                    .resource(resource)
                    .reporter(reporter)
                    .reason(request.reason())
                    .details(details == null || details.isEmpty() ? null : details)
                    .build());
            return ReportResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.REPORT_ALREADY_OPEN);
        }
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> search(ReportStatus status, Pageable pageable) {
        return reportRepository.search(status, pageable).map(ReportResponse::from);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return reportRepository.countByStatus(ReportStatus.OPEN);
    }

    @Transactional
    public ReportResponse resolve(Long id, ReportResolveRequest request, String adminUsername) {
        // SELECT ... FOR UPDATE: a second admin blocks here until the first commits,
        // then reads RESOLVED/DISMISSED and gets 409 — no overwrite, no second notification.
        ResourceReport report = reportRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new AppException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean resolved = request.action() == ReportResolveRequest.Action.RESOLVE;
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        report.setStatus(resolved ? ReportStatus.RESOLVED : ReportStatus.DISMISSED);
        report.setResolutionNote(note);
        report.setResolvedBy(admin);
        report.setResolvedAt(LocalDateTime.now());

        Resource resource = report.getResource();
        boolean takenDown = false;
        if (resolved && request.hideResource() && resource.getStatus() != ResourceStatus.REJECTED) {
            // Take-down is a moderation STATUS, not a visibility change: the owner can
            // flip visibility freely, but leaving REJECTED requires editing the resource
            // (which forces PENDING) and a new admin approval.
            String reason = "Gỡ do vi phạm (báo cáo #" + report.getId() + ": "
                    + REASON_LABELS.getOrDefault(report.getReason(), report.getReason().name()) + ")"
                    + (note == null ? "" : ". " + note);
            resource.setStatus(ResourceStatus.REJECTED);
            resource.setRejectionReason(reason);
            resourceRepository.save(resource);
            takenDown = true;
            notificationService.notify(resource.getCreatedBy(), NotificationType.RESOURCE_REJECTED,
                    "Tài liệu của bạn đã bị gỡ do vi phạm",
                    "\"" + resource.getTitle() + "\" đã bị gỡ khỏi kho học liệu. " + reason
                            + ". Bạn có thể chỉnh sửa và gửi duyệt lại.",
                    "/teacher/my-resources");
        }

        notificationService.notify(report.getReporter().getId(),
                resolved ? NotificationType.REPORT_RESOLVED : NotificationType.REPORT_DISMISSED,
                resolved ? "Báo cáo vi phạm đã được xử lý" : "Báo cáo vi phạm đã được xem xét",
                "Báo cáo của bạn về \"" + resource.getTitle() + "\" "
                        + (resolved ? "đã được xác nhận và xử lý." : "không được xác nhận là vi phạm.")
                        + (note == null ? "" : " Ghi chú: " + note),
                "/resources/" + resource.getSlug());

        try {
            auditLogService.saveLog(resolved ? "RESOLVE_REPORT" : "DISMISS_REPORT", adminUsername, "RESOURCE_REPORT",
                    "Report #" + report.getId() + " on: " + resource.getTitle()
                            + (takenDown ? " | resource taken down (REJECTED)" : ""),
                    "UNKNOWN", "UNKNOWN");
        } catch (Exception ignored) {
            // Audit logging must never block moderation.
        }
        return ReportResponse.from(report);
    }
}
