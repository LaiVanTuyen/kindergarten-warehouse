package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, String> {
    Page<Resource> findByTopicIdAndIsDeletedFalse(Long topicId, Pageable pageable);

    Page<Resource> findByTopicCategoryIdAndIsDeletedFalse(Long categoryId, Pageable pageable);

    Page<Resource> findByIsDeletedFalse(Pageable pageable);
}
