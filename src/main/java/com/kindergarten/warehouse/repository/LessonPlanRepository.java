package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.LessonPlan;
import com.kindergarten.warehouse.entity.LessonPlanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LessonPlanRepository extends JpaRepository<LessonPlan, Long> {

    @EntityGraph(attributePaths = {"owner", "ageGroup", "topic"})
    @Query(value = "SELECT p FROM LessonPlan p WHERE p.owner.id = :ownerId "
            + "AND (:keyword IS NULL OR LOWER(p.title) LIKE :keyword ESCAPE '!' OR LOWER(p.objectives) LIKE :keyword ESCAPE '!')",
            countQuery = "SELECT COUNT(p) FROM LessonPlan p WHERE p.owner.id = :ownerId "
                    + "AND (:keyword IS NULL OR LOWER(p.title) LIKE :keyword ESCAPE '!' OR LOWER(p.objectives) LIKE :keyword ESCAPE '!')")
    Page<LessonPlan> findByOwner(@Param("ownerId") Long ownerId, @Param("keyword") String keyword, Pageable pageable);

    /**
     * Shared plans or templates. {@code keyword} is lower-cased and LIKE-escaped with "!";
     * null filters are ignored. Searches title and objectives ("tìm theo mục tiêu").
     */
    @EntityGraph(attributePaths = {"owner", "ageGroup", "topic"})
    @Query(value = "SELECT p FROM LessonPlan p WHERE "
            + "(:templates = true AND p.template = true OR :templates = false AND p.status = :status) "
            + "AND (:keyword IS NULL OR LOWER(p.title) LIKE :keyword ESCAPE '!' "
            + "OR LOWER(p.objectives) LIKE :keyword ESCAPE '!') "
            + "AND (:ageGroupId IS NULL OR p.ageGroup.id = :ageGroupId)",
            countQuery = "SELECT COUNT(p) FROM LessonPlan p WHERE "
                    + "(:templates = true AND p.template = true OR :templates = false AND p.status = :status) "
                    + "AND (:keyword IS NULL OR LOWER(p.title) LIKE :keyword ESCAPE '!' "
                    + "OR LOWER(p.objectives) LIKE :keyword ESCAPE '!') "
                    + "AND (:ageGroupId IS NULL OR p.ageGroup.id = :ageGroupId)")
    Page<LessonPlan> search(@Param("templates") boolean templates, @Param("status") LessonPlanStatus status,
            @Param("keyword") String keyword, @Param("ageGroupId") Long ageGroupId, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "ageGroup", "topic"})
    @Query("SELECT p FROM LessonPlan p WHERE p.id = :id")
    Optional<LessonPlan> findWithDetailsById(@Param("id") Long id);

    long countByOwnerId(Long ownerId);
}
