package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Category> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
    java.util.List<Category> findAllByIsDeletedFalse();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
    java.util.List<Category> findAllByIsDeletedFalseAndIsActiveTrue();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
    java.util.List<Category> findAllByIsDeletedTrue();

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
    java.util.Optional<Category> findById(Long id);

    // Prevent accidental usage of findAll which returns deleted ones
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = { "creator", "updater" })
    default java.util.List<Category> findAll() {
        return findAllByIsDeletedFalse();
    }

    // Duplicate validation methods
    boolean existsBySlugAndIsDeletedFalse(String slug);

    boolean existsByNameAndIsDeletedFalse(String name);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Category c SET c.isDeleted = true WHERE c.id IN :ids")
    void softDeleteAllByIds(@org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Category c WHERE c.id IN :ids")
    void hardDeleteAllByIds(@org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Category c SET c.isDeleted = false WHERE c.id IN :ids")
    void restoreAllByIds(@org.springframework.data.repository.query.Param("ids") java.util.List<Long> ids);

    java.util.List<com.kindergarten.warehouse.repository.projection.CategoryIconProjection> findAllProjectedByIdIn(
            java.util.List<Long> ids);
}
