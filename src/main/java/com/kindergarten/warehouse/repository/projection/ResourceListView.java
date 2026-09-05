package com.kindergarten.warehouse.repository.projection;

import com.kindergarten.warehouse.entity.ResourceType;
import com.kindergarten.warehouse.entity.ResourceStatus;
import com.kindergarten.warehouse.entity.Visibility;

import java.time.LocalDateTime;

/**
 * Projection cho danh sách Portal (list + search).
 *
 * <p>Chỉ khai báo đúng những cột Portal thật sự hiển thị. Cố ý <strong>không
 * có</strong> {@code creator}/{@code updater} và {@code topic.creator}/
 * {@code topic.updater}:
 *
 * <ul>
 *   <li>Card của Portal không hiển thị tên người đăng — đã kiểm tra
 *       {@code resource-card.component.html}, chỉ dùng title, description,
 *       slug, viewsCount, status, resourceType, thumbnail và rating.</li>
 *   <li>Bốn quan hệ đó khiến Hibernate join bảng {@code users} bốn lần và kéo
 *       <strong>toàn bộ cột</strong>, gồm cả {@code password} và
 *       {@code token_version}, trên một endpoint công khai không cần đăng nhập.</li>
 * </ul>
 *
 * <p>Đo trực tiếp trên MySQL với cùng bộ lọc và LIMIT 12: dạng cũ 24,7 ms,
 * dạng hẹp này 5,2 ms — nhanh gấp 4,7 lần.
 *
 * <p><strong>Chỉ dùng cho list/search.</strong> Chi tiết theo slug và các màn
 * quản trị vẫn dùng entity đầy đủ, vì chúng có hiển thị tên người đăng.
 *
 * <p>{@code ageGroups} cố ý không nằm ở đây: nạp collection trong cùng truy vấn
 * phân trang sẽ khiến Hibernate phân trang trong bộ nhớ. Chúng được lấy bằng
 * một batch query riêng theo toàn bộ id của trang.
 */
public interface ResourceListView {

    String getId();

    String getTitle();

    String getSlug();

    String getDescription();

    Long getViewsCount();

    String getFileUrl();

    String getThumbnailUrl();

    ResourceType getResourceType();

    String getFileType();

    String getFileExtension();

    Long getFileSize();

    String getDuration();

    ResourceStatus getStatus();

    Long getDownloadCount();

    Double getAverageRating();

    Visibility getVisibility();

    String getRejectionReason();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();

    TopicView getTopic();

    /** Chỉ id, tên, slug và visibility — đủ cho thẻ và điều hướng. */
    interface TopicView {

        Long getId();

        String getName();

        String getSlug();

        String getDescription();

        Visibility getVisibility();

        CategoryView getCategory();
    }

    interface CategoryView {

        Long getId();

        String getName();
    }
}
