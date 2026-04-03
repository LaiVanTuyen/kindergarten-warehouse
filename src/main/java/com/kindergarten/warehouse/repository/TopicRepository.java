package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long>,
                org.springframework.data.jpa.repository.JpaSpecificationExecutor<Topic> {
        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT t FROM Topic t WHERE t.category.id = :categoryId AND t.isDeleted = false AND t.category.isDeleted = false")
        java.util.List<Topic> findByCategoryIdAndIsDeletedFalse(
                        @org.springframework.data.repository.query.Param("categoryId") Long categoryId);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT t FROM Topic t WHERE t.category.id = :categoryId AND t.isDeleted = false AND t.isActive = true AND t.category.isDeleted = false")
        java.util.List<Topic> findByCategoryIdAndIsDeletedFalseAndIsActiveTrue(
                        @org.springframework.data.repository.query.Param("categoryId") Long categoryId);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT t FROM Topic t WHERE t.category.id = :categoryId AND t.isDeleted = true")
        java.util.List<Topic> findByCategoryIdAndIsDeletedTrue(
                        @org.springframework.data.repository.query.Param("categoryId") Long categoryId);

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT t FROM Topic t WHERE t.isDeleted = false AND t.category.isDeleted = false")
        java.util.List<Topic> findAllByIsDeletedFalse();

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT t FROM Topic t WHERE t.isDeleted = false AND t.isActive = true AND t.category.isDeleted = false")
        java.util.List<Topic> findAllByIsDeletedFalseAndIsActiveTrue();

        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
        @org.springframework.data.jpa.repository.Query("SELECT t FROM Topic t WHERE t.isDeleted = true")
        java.util.List<Topic> findAllByIsDeletedTrue();

        @Override
        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "category", "creator", "updater" })
        java.util.Optional<Topic> findById(Long id);

        @Override
        @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
        default java.util.List<Topic> findAll() {
                return findAllByIsDeletedFalse();
        }

        // Duplicate validation methods
        boolean existsByNameAndIsDeletedFalse(String name);
}
