package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, String>,
                org.springframework.data.jpa.repository.JpaSpecificationExecutor<Resource> {
        // Preview needs only access-policy fields. Never join creator/updater users.
        @org.springframework.data.jpa.repository.Query("SELECT r FROM Resource r " +
                        "LEFT JOIN FETCH r.topic t LEFT JOIN FETCH t.category WHERE r.id = :id")
        java.util.Optional<Resource> findForPreviewAccess(@org.springframework.data.repository.query.Param("id") String id);
        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "topic", "topic.category",
                        "topic.creator", "topic.updater", "creator", "updater" })
        Page<Resource> findAll(org.springframework.data.jpa.domain.Specification<Resource> spec, Pageable pageable);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "topic", "topic.category",
                        "topic.creator", "topic.updater", "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT r FROM Resource r WHERE r.topic.id = :topicId AND r.isDeleted = false AND r.topic.isDeleted = false AND r.topic.category.isDeleted = false")
        Page<Resource> findByTopicIdAndIsDeletedFalse(
                        @org.springframework.data.repository.query.Param("topicId") Long topicId, Pageable pageable);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "topic", "topic.category",
                        "topic.creator", "topic.updater", "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT r FROM Resource r WHERE r.topic.category.id = :categoryId AND r.isDeleted = false AND r.topic.isDeleted = false AND r.topic.category.isDeleted = false")
        Page<Resource> findByTopicCategoryIdAndIsDeletedFalse(
                        @org.springframework.data.repository.query.Param("categoryId") Long categoryId,
                        Pageable pageable);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "topic", "topic.category",
                        "topic.creator", "topic.updater", "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT r FROM Resource r WHERE r.isDeleted = false AND r.topic.isDeleted = false AND r.topic.category.isDeleted = false")
        Page<Resource> findByIsDeletedFalse(Pageable pageable);

        @org.springframework.data.jpa.repository.Query("SELECT r FROM Resource r " +
                        "JOIN FETCH r.topic t " +
                        "LEFT JOIN FETCH t.category " +
                        "LEFT JOIN FETCH t.creator " +
                        "LEFT JOIN FETCH t.updater " +
                        "LEFT JOIN FETCH r.creator " +
                        "LEFT JOIN FETCH r.updater " +
                        "LEFT JOIN FETCH r.ageGroups " +
                        "WHERE r.slug = :slug")
        java.util.Optional<Resource> findBySlug(@org.springframework.data.repository.query.Param("slug") String slug);

        @org.springframework.data.jpa.repository.Query("SELECT r FROM Resource r " +
                        "JOIN FETCH r.topic t " +
                        "LEFT JOIN FETCH t.category " +
                        "LEFT JOIN FETCH t.creator " +
                        "LEFT JOIN FETCH t.updater " +
                        "LEFT JOIN FETCH r.creator " +
                        "LEFT JOIN FETCH r.updater " +
                        "LEFT JOIN FETCH r.ageGroups " +
                        "WHERE r.id = :id")
        java.util.Optional<Resource> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") String id);

        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.transaction.annotation.Transactional
        @org.springframework.data.jpa.repository.Query("UPDATE Resource r SET r.viewsCount = r.viewsCount + :count WHERE r.id = :id")
        void incrementViews(@org.springframework.data.repository.query.Param("id") String id,
                        @org.springframework.data.repository.query.Param("count") long count);

        @org.springframework.data.jpa.repository.Modifying
        @org.springframework.transaction.annotation.Transactional
        @org.springframework.data.jpa.repository.Query("UPDATE Resource r SET r.downloadCount = r.downloadCount + :count WHERE r.id = :id")
        void incrementDownloads(@org.springframework.data.repository.query.Param("id") String id,
                        @org.springframework.data.repository.query.Param("count") long count);

        @org.springframework.data.jpa.repository.Query("SELECT r.id, t FROM Resource r JOIN r.tags t WHERE r.id IN :resourceIds")
        java.util.List<Object[]> findTagsByResourceIds(
                        @org.springframework.data.repository.query.Param("resourceIds") java.util.Collection<String> resourceIds);

        /** Tag cloud: counts only resources a guest could see (approved, public chain, not deleted). */
        @org.springframework.data.jpa.repository.Query("SELECT t, COUNT(r) FROM Resource r JOIN r.tags t " +
                        "WHERE r.isDeleted = false AND r.status = com.kindergarten.warehouse.entity.ResourceStatus.APPROVED " +
                        "AND r.visibility = com.kindergarten.warehouse.entity.Visibility.PUBLIC " +
                        "AND r.topic.isDeleted = false AND r.topic.visibility = com.kindergarten.warehouse.entity.Visibility.PUBLIC " +
                        "AND r.topic.category.isDeleted = false AND r.topic.category.visibility = com.kindergarten.warehouse.entity.Visibility.PUBLIC " +
                        "GROUP BY t ORDER BY COUNT(r) DESC, t ASC")
        java.util.List<Object[]> findPopularTags(Pageable pageable);

        /** Author page totals over the author's publicly visible resources: [count, views, downloads]. */
        @org.springframework.data.jpa.repository.Query("SELECT COUNT(r), COALESCE(SUM(r.viewsCount), 0), COALESCE(SUM(r.downloadCount), 0) " +
                        "FROM Resource r WHERE r.createdBy = :userId " +
                        "AND r.isDeleted = false AND r.status = com.kindergarten.warehouse.entity.ResourceStatus.APPROVED " +
                        "AND r.visibility = com.kindergarten.warehouse.entity.Visibility.PUBLIC " +
                        "AND r.topic.isDeleted = false AND r.topic.visibility = com.kindergarten.warehouse.entity.Visibility.PUBLIC " +
                        "AND r.topic.category.isDeleted = false AND r.topic.category.visibility = com.kindergarten.warehouse.entity.Visibility.PUBLIC")
        java.util.List<Object[]> findPublicStatsByAuthor(@org.springframework.data.repository.query.Param("userId") Long userId);

        /** Resources for a collection page, with everything ResourceAccessGuard and the mapper touch. */
        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Resource r " +
                        "LEFT JOIN FETCH r.topic t LEFT JOIN FETCH t.category " +
                        "LEFT JOIN FETCH r.creator WHERE r.id IN :ids")
        java.util.List<Resource> findAllWithDetailsByIdIn(
                        @org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);
}
