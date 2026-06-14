package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT AVG(c.rating) FROM Comment c WHERE c.resource.id = :resourceId")
    Double getAverageRatingByResourceId(String resourceId);

    Page<Comment> findByResourceId(String resourceId, Pageable pageable);

    /**
     * Recalc average_rating nguyên tử ngay trong DB, tránh race read-modify-write
     * khi nhiều comment được tạo/xóa đồng thời ([ARC-4]). Bulk JPQL không tăng
     * cột version của Resource nên không gây xung đột optimistic lock.
     */
    @Modifying
    @Query("UPDATE Resource r SET r.averageRating = "
            + "COALESCE((SELECT AVG(c.rating) FROM Comment c WHERE c.resource.id = :resourceId), 0) "
            + "WHERE r.id = :resourceId")
    void recalculateAverageRating(@Param("resourceId") String resourceId);
}
