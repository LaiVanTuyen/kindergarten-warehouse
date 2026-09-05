package com.kindergarten.warehouse.security;

import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Resource;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.entity.Visibility;
import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Cổng kiểm tra dùng chung cho mọi đường đọc một tài nguyên đơn lẻ:
 * chi tiết theo slug, chi tiết theo id, danh sách liên quan, và tải file.
 *
 * <p>Tồn tại để <strong>thứ tự kiểm tra chỉ được định nghĩa một lần</strong>.
 * Nếu mỗi endpoint tự viết, chúng sẽ trả 404/410 khác nhau cho cùng một tình
 * huống, và chỉ cần một chỗ đảo thứ tự là lộ sự tồn tại của tài nguyên.
 *
 * <h2>Thứ tự cố định (BUSINESS_RULES_V1 §4.3)</h2>
 * <ol>
 *   <li>Tìm tài nguyên — <strong>việc của caller</strong>, và repository
 *       <strong>không được lọc sẵn</strong> {@code ARCHIVED} hay
 *       {@code isDeleted}. Lọc ở tầng truy vấn thì không còn phân biệt được
 *       "đã gỡ" với "không tồn tại", nên không thể trả 410.</li>
 *   <li>Tính visibility hiệu lực theo chuỗi Category → Topic → Resource.</li>
 *   <li>Hỏi {@link VisibilityPolicy} xem người này có được nhìn thấy không.</li>
 *   <li>Không có quyền → <strong>404</strong>, không tiết lộ sự tồn tại.</li>
 *   <li>Có quyền nhưng {@code ARCHIVED} → <strong>410</strong>.</li>
 *   <li>Còn hiệu lực và có quyền → trả tài nguyên.</li>
 * </ol>
 *
 * <p>Bước 4 phải đứng trước bước 5. Đảo lại thì tài nguyên {@code INTERNAL} đã
 * archive sẽ trả 410 cho khách, và người ngoài suy ra được nó có tồn tại.
 */
@Component
public class ResourceAccessGuard {

    /**
     * Trả về tài nguyên nếu người xem được phép thấy, ngược lại ném lỗi phù hợp.
     *
     * @param resource tài nguyên đã nạp, có thể {@code null} khi không tìm thấy
     * @param viewer   người đang xem; dùng {@link Viewer#guest()} cho khách
     * @throws AppException {@code RESOURCE_NOT_FOUND} (404) hoặc
     *                      {@code RESOURCE_ARCHIVED} (410)
     */
    public Resource requireViewable(Resource resource, Viewer viewer) {
        // Bước 1 — không tìm thấy, hoặc đã xoá mềm: 404 cho tất cả, kể cả ADMIN.
        // Tài nguyên đã xoá có đường quản trị riêng, không đi qua cổng này.
        if (resource == null || Boolean.TRUE.equals(resource.getIsDeleted())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Long ownerId = resource.getCreatedBy();

        // Bước 2 + 3
        Visibility effective = effectiveVisibilityOf(resource);
        boolean canView = VisibilityPolicy.canView(viewer, effective, ownerId);

        // Bước 4 — không có quyền thì luôn là 404, bất kể lý do.
        if (!canView) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // Bước 5 — có quyền, nhưng đã gỡ khỏi Portal.
        if (resource.getStatus() == ResourceStatus.ARCHIVED) {
            throw new AppException(ErrorCode.RESOURCE_ARCHIVED);
        }

        // Bước 6 — DRAFT/PENDING/REJECTED chỉ chủ sở hữu và ADMIN được xem.
        // Có quyền theo visibility vẫn chưa đủ: status là cổng độc lập (§3.4).
        if (resource.getStatus() != ResourceStatus.APPROVED
                && !viewer.isAdmin()
                && !viewer.owns(ownerId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return resource;
    }

    /**
     * Như {@link #requireViewable} nhưng chặt hơn đúng một bậc: khách không tải
     * được kể cả tài nguyên {@code PUBLIC} (§8.4).
     *
     * <p>Kiểm quyền xem <strong>trước</strong>, rồi mới kiểm đăng nhập. Làm
     * ngược lại thì khách gọi vào một tài nguyên không tồn tại sẽ nhận 401 thay
     * vì 404 — tức là biết được "id này có thật".
     */
    public Resource requireDownloadable(Resource resource, Viewer viewer) {
        Resource viewable = requireViewable(resource, viewer);

        // Drafts are never downloadable, including by owner/admin. A draft may
        // not have a file yet and BUSINESS_RULES §4.2 excludes it absolutely
        // from every download path.
        if (viewable.getStatus() == ResourceStatus.DRAFT) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (!viewer.isAuthenticated()) {
            throw new AppException(ErrorCode.DOWNLOAD_REQUIRES_AUTH);
        }

        return viewable;
    }

    /**
     * Visibility hiệu lực = mức chặt nhất của Category → Topic → Resource (§3.2).
     *
     * <p>Fail-closed: nếu Topic hoặc Category bị xoá mềm thì coi cả chuỗi là
     * {@link Visibility#PRIVATE}. Nhờ vậy chủ sở hữu và ADMIN vẫn truy được tài
     * nguyên của mình khi danh mục cha bị xoá nhầm, còn người ngoài nhận 404.
     */
    public Visibility effectiveVisibilityOf(Resource resource) {
        Topic topic = resource.getTopic();
        Category category = (topic == null) ? null : topic.getCategory();

        if (topic == null || category == null
                || Boolean.TRUE.equals(topic.getIsDeleted())
                || Boolean.TRUE.equals(category.getIsDeleted())) {
            return Visibility.PRIVATE;
        }

        return VisibilityPolicy.effectiveVisibility(
                category.getVisibility(),
                topic.getVisibility(),
                resource.getVisibility());
    }
}
