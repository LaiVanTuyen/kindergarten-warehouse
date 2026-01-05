package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Resource> {
    Page<Resource> findByTopicIdAndIsDeletedFalse(Long topicId, Pageable pageable);

    Page<Resource> findByTopicCategoryIdAndIsDeletedFalse(Long categoryId, Pageable pageable);

    Page<Resource> findByIsDeletedFalse(Pageable pageable);

    java.util.Optional<Resource> findBySlug(String slug);

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
}
