package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {
    Page<Resource> findByTopicId(Long topicId, Pageable pageable);

    Page<Resource> findByTopicCategoryId(Long categoryId, Pageable pageable);
}
