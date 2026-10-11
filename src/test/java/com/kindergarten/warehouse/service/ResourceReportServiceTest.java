package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.dto.request.ReportCreateRequest;
import com.kindergarten.warehouse.dto.request.ReportResolveRequest;
import com.kindergarten.warehouse.entity.NotificationType;
import com.kindergarten.warehouse.entity.ReportReason;
import com.kindergarten.warehouse.entity.ReportStatus;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceReport;
import com.kindergarten.warehouse.entity.Role;
import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.repository.ResourceReportRepository;
import com.kindergarten.warehouse.repository.ResourceRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.security.ResourceAccessGuard;
import com.kindergarten.warehouse.security.Viewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceReportServiceTest {

    @Mock
    private ResourceReportRepository reportRepository;
    @Mock
    private ResourceRepository resourceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ResourceAccessGuard resourceAccessGuard;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ResourceReportService service;

    private Resource resource;
    private User reporter;
    private final Viewer viewer = Viewer.of(7L, Set.of(Role.USER));

    @BeforeEach
    void setUp() {
        resource = new Resource();
        resource.setId("res-1");
        resource.setTitle("Bài hát mầm non");
        resource.setSlug("bai-hat-mam-non");
        resource.setVisibility(Visibility.PUBLIC);
        resource.setCreatedBy(3L);
        reporter = new User();
        reporter.setId(7L);
        reporter.setUsername("parent");
    }

    @Test
    void duplicateOpenReportIsRejected() {
        when(resourceRepository.findByIdWithDetails("res-1")).thenReturn(Optional.of(resource));
        when(resourceAccessGuard.requireViewable(resource, viewer)).thenReturn(resource);
        when(reportRepository.existsByResourceIdAndReporterIdAndStatus("res-1", 7L, ReportStatus.OPEN))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new ReportCreateRequest("res-1", ReportReason.COPYRIGHT, null), viewer))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_OPEN);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void resolvingWithTakeDownRejectsResourceAndNotifiesBothSides() {
        ResourceReport report = ResourceReport.builder()
                .id(1L).resource(resource).reporter(reporter)
                .reason(ReportReason.COPYRIGHT).status(ReportStatus.OPEN).build();
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(reportRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(report));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        resource.setStatus(com.kindergarten.warehouse.entity.ResourceStatus.APPROVED);

        var response = service.resolve(1L,
                new ReportResolveRequest(ReportResolveRequest.Action.RESOLVE, "Vi phạm bản quyền", true), "admin");

        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        // Take-down is a moderation status, not visibility: owner can't undo it by toggling visibility.
        assertThat(resource.getStatus()).isEqualTo(com.kindergarten.warehouse.entity.ResourceStatus.REJECTED);
        assertThat(resource.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(resource.getRejectionReason()).contains("báo cáo #1").contains("Vi phạm bản quyền");
        verify(notificationService).notify(eq(3L), eq(NotificationType.RESOURCE_REJECTED), anyString(), anyString(), anyString());
        verify(notificationService).notify(eq(7L), eq(NotificationType.REPORT_RESOLVED), anyString(), anyString(), anyString());
    }

    @Test
    void alreadyHandledReportCannotBeResolvedAgain() {
        ResourceReport report = ResourceReport.builder()
                .id(1L).resource(resource).reporter(reporter)
                .reason(ReportReason.SPAM).status(ReportStatus.DISMISSED).build();
        when(reportRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.resolve(1L,
                new ReportResolveRequest(ReportResolveRequest.Action.DISMISS, null, false), "admin"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPORT_ALREADY_RESOLVED);
    }
}
