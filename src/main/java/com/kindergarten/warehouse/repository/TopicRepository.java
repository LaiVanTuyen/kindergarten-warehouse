package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Topic;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long>, JpaSpecificationExecutor<Topic> {

    @EntityGraph(attributePaths = { "creator", "updater" })
    @Query("SELECT t FROM Topic t WHERE t.category.id = :categoryId AND t.isDeleted = false AND t.category.isDeleted = false")
    List<Topic> findByCategoryIdAndIsDeletedFalse(@Param("categoryId") Long categoryId);

    @EntityGraph(attributePaths = { "creator", "updater" })
    @Query("SELECT t FROM Topic t WHERE t.category.id = :categoryId AND t.isDeleted = true")
    List<Topic> findByCategoryIdAndIsDeletedTrue(@Param("categoryId") Long categoryId);

    @EntityGraph(attributePaths = { "creator", "updater" })
    @Query("SELECT t FROM Topic t WHERE t.isDeleted = false AND t.category.isDeleted = false")
    List<Topic> findAllByIsDeletedFalse();

    @EntityGraph(attributePaths = { "creator", "updater" })
    @Query("SELECT t FROM Topic t WHERE t.isDeleted = true")
    List<Topic> findAllByIsDeletedTrue();

    @Override
    @EntityGraph(attributePaths = { "category", "creator", "updater" })
    Optional<Topic> findById(Long id);

    @Override
    @EntityGraph(attributePaths = { "creator", "updater" })
    default List<Topic> findAll() {
        return findAllByIsDeletedFalse();
    }

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsBySlugAndIsDeletedFalse(String slug);

    @Query("""
            SELECT r.topic.id, COUNT(r) FROM Resource r
            WHERE r.isDeleted = false AND r.topic.id IN :topicIds
            GROUP BY r.topic.id
            """)
    List<Object[]> countActiveResourcesByTopicIds(@Param("topicIds") java.util.Collection<Long> topicIds);
}
