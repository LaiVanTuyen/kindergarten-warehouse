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
            + "AND (:ageGroupId IS NULL OR p.ageGroup.id = :ageGroupId) "
            + "AND (:approvedOnly = false OR p.reviewStatus = com.kindergarten.warehouse.entity.PlanReviewStatus.APPROVED)",
            countQuery = "SELECT COUNT(p) FROM LessonPlan p WHERE "
                    + "(:templates = true AND p.template = true OR :templates = false AND p.status = :status) "
                    + "AND (:keyword IS NULL OR LOWER(p.title) LIKE :keyword ESCAPE '!' "
                    + "OR LOWER(p.objectives) LIKE :keyword ESCAPE '!') "
                    + "AND (:ageGroupId IS NULL OR p.ageGroup.id = :ageGroupId) "
                    + "AND (:approvedOnly = false OR p.reviewStatus = com.kindergarten.warehouse.entity.PlanReviewStatus.APPROVED)")
    Page<LessonPlan> search(@Param("templates") boolean templates, @Param("status") LessonPlanStatus status,
            @Param("keyword") String keyword, @Param("ageGroupId") Long ageGroupId,
            @Param("approvedOnly") boolean approvedOnly, Pageable pageable);

    /** Review queue: plans waiting for "duyệt chuyên môn", oldest request first. */
    @EntityGraph(attributePaths = {"owner", "ageGroup", "topic"})
    @Query(value = "SELECT p FROM LessonPlan p WHERE p.reviewStatus = :reviewStatus",
            countQuery = "SELECT COUNT(p) FROM LessonPlan p WHERE p.reviewStatus = :reviewStatus")
    Page<LessonPlan> findByReviewStatus(@Param("reviewStatus") com.kindergarten.warehouse.entity.PlanReviewStatus reviewStatus,
            Pageable pageable);

    /**
     * Owner asks for review. Bulk JPQL update: the row's @Version is NOT bumped, so an editor
     * the owner has open keeps working. Returns 0 when the plan is not shared or already pending/approved.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LessonPlan p SET p.reviewStatus = com.kindergarten.warehouse.entity.PlanReviewStatus.PENDING, "
            + "p.reviewNote = NULL, p.reviewedBy = NULL, p.reviewedAt = NULL "
            + "WHERE p.id = :id AND p.status = com.kindergarten.warehouse.entity.LessonPlanStatus.SHARED "
            + "AND p.reviewStatus IN (com.kindergarten.warehouse.entity.PlanReviewStatus.NONE, "
            + "com.kindergarten.warehouse.entity.PlanReviewStatus.CHANGES_REQUESTED)")
    int markPending(@Param("id") Long id);

    /**
     * Reviewer decision, only if the plan is still PENDING and still at the version the
     * reviewer read — so nobody approves content they did not see, and two reviewers can't
     * both win. Returns 0 otherwise.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LessonPlan p SET p.reviewStatus = :decision, p.reviewNote = :note, "
            + "p.reviewedBy = :reviewerId, p.reviewedAt = CURRENT_TIMESTAMP "
            + "WHERE p.id = :id AND p.version = :version "
            + "AND p.reviewStatus = com.kindergarten.warehouse.entity.PlanReviewStatus.PENDING")
    int decideReview(@Param("id") Long id, @Param("version") Long version,
            @Param("decision") com.kindergarten.warehouse.entity.PlanReviewStatus decision,
            @Param("note") String note, @Param("reviewerId") Long reviewerId);

    @EntityGraph(attributePaths = {"owner", "ageGroup", "topic"})
    @Query("SELECT p FROM LessonPlan p WHERE p.id = :id")
    Optional<LessonPlan> findWithDetailsById(@Param("id") Long id);

    /** Back to NONE (content changed after approval, or plan no longer shared). */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("UPDATE LessonPlan p SET p.reviewStatus = com.kindergarten.warehouse.entity.PlanReviewStatus.NONE, "
            + "p.reviewNote = NULL, p.reviewedBy = NULL, p.reviewedAt = NULL WHERE p.id = :id")
    int resetReview(@Param("id") Long id);

    long countByOwnerId(Long ownerId);
}
