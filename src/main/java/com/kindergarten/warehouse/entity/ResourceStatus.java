package com.kindergarten.warehouse.entity;

/**
 * Vòng đời tài nguyên (BUSINESS_RULES_V1 §4).
 *
 * <pre>
 * DRAFT ──submit──► PENDING ──approve──► APPROVED ──archive──► ARCHIVED
 *                      └────reject────► REJECTED ──submit──► PENDING
 * </pre>
 *
 * <p>Độc lập với {@link Visibility}: một tài nguyên {@code APPROVED} +
 * {@code PRIVATE} là hợp lệ.
 */
public enum ResourceStatus {

    /** Nháp của người tạo. Chưa gửi duyệt, không hiện ở bất kỳ đâu ngoài "Tài liệu của tôi". */
    DRAFT,

    /** Đã gửi duyệt, đang chờ admin xử lý. */
    PENDING,

    /** Đã duyệt. Chỉ trạng thái này mới lên Portal. */
    APPROVED,

    /** Bị từ chối, kèm lý do ở {@code rejectionReason}. */
    REJECTED,

    /**
     * Đã gỡ khỏi Portal nhưng không xoá. Người từng có quyền xem sẽ nhận
     * <strong>410 Gone</strong>, không phải 404 — xem §4.3.
     */
    ARCHIVED
}
