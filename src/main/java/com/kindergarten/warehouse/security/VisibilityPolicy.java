package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Visibility;

/**
 * Nơi <strong>duy nhất</strong> quyết định ai được xem gì theo visibility.
 * Hiện thực BUSINESS_RULES_V1 §3.2, §3.3 và §8.4.
 *
 * <p>Trước đây logic này nằm rải rác ở 22 biểu thức {@code == Visibility.PUBLIC}
 * trong 10 file. Mọi nơi cần quyết định hiển thị phải gọi vào đây; không viết
 * lại phép so sánh ở service hay specification.
 *
 * <p><strong>Phạm vi:</strong> class này chỉ trả lời về <em>visibility</em>.
 * {@code status} (DRAFT/PENDING/APPROVED/ARCHIVED) và {@code isDeleted} là hai
 * cổng <em>độc lập</em>, phải kiểm riêng bên cạnh — xem §3.4 và §4.
 *
 * <p>Không phụ thuộc Spring: kiểm thử được bằng unit test thuần.
 */
public final class VisibilityPolicy {

    private VisibilityPolicy() {
    }

    /**
     * Visibility hiệu lực của một tài nguyên = mức chặt nhất trong chuỗi
     * Category → Topic → Resource (§3.2).
     *
     * <p>Ví dụ: Resource {@code PUBLIC} nằm trong Topic {@code INTERNAL} thì
     * hiệu lực là {@code INTERNAL} — khách không thấy.
     *
     * <p>Fail-closed: bất kỳ mắt xích nào {@code null} đều tính là
     * {@link Visibility#PRIVATE}.
     */
    public static Visibility effectiveVisibility(Visibility category,
                                                 Visibility topic,
                                                 Visibility resource) {
        return Visibility.mostRestrictive(category, topic, resource);
    }

    /**
     * Người xem có được <em>xem</em> tài nguyên với visibility hiệu lực này không (§3.3).
     *
     * <p>Chưa bao gồm kiểm tra {@code status} và {@code isDeleted}.
     *
     * @param viewer    người đang xem; dùng {@link Viewer#guest()} cho khách,
     *                  không truyền {@code null}
     * @param effective visibility hiệu lực, lấy từ
     *                  {@link #effectiveVisibility(Visibility, Visibility, Visibility)}
     * @param ownerId   id người tạo tài nguyên, có thể {@code null}
     */
    public static boolean canView(Viewer viewer, Visibility effective, Long ownerId) {
        requireViewer(viewer);

        // ADMIN xem được mọi thứ; chủ sở hữu luôn xem được đồ của mình.
        if (viewer.isAdmin() || viewer.owns(ownerId)) {
            return true;
        }

        // Fail-closed: visibility không xác định thì không cho xem.
        if (effective == null) {
            return false;
        }

        return switch (effective) {
            case PUBLIC -> true;
            case INTERNAL -> viewer.isAuthenticated();
            case PRIVATE -> false; // chủ sở hữu và ADMIN đã được xử lý ở trên
        };
    }

    /**
     * Người xem có được <em>tải file</em> không (§8.4).
     *
     * <p>Chặt hơn {@link #canView} đúng một bậc: <strong>khách không tải được
     * kể cả tài nguyên PUBLIC</strong>. Lý do: chống bot tải hàng loạt, truy vết
     * được ai đã tải, và áp rate limit theo tài khoản.
     *
     * <p>Tài nguyên {@code YOUTUBE} và {@code EXTERNAL_LINK} không đi qua
     * endpoint tải nên không chịu ràng buộc này.
     */
    public static boolean canDownload(Viewer viewer, Visibility effective, Long ownerId) {
        requireViewer(viewer);
        return viewer.isAuthenticated() && canView(viewer, effective, ownerId);
    }

    /**
     * Tiện ích cho lời gọi có sẵn cả ba mắt xích: gộp
     * {@link #effectiveVisibility} và {@link #canView} làm một.
     */
    public static boolean canView(Viewer viewer,
                                  Visibility category,
                                  Visibility topic,
                                  Visibility resource,
                                  Long ownerId) {
        return canView(viewer, effectiveVisibility(category, topic, resource), ownerId);
    }

    private static void requireViewer(Viewer viewer) {
        if (viewer == null) {
            throw new IllegalArgumentException(
                    "Viewer khong duoc null — dung Viewer.guest() cho khach chua dang nhap");
        }
    }
}
