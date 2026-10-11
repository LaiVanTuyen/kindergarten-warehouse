package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.PlanComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** Comments, plus the native plan_usages upsert/counts (no entity needed for a counter row). */
@Repository
public interface PlanCollaborationRepository extends JpaRepository<PlanComment, Long> {

    @EntityGraph(attributePaths = {"author"})
    @Query("SELECT c FROM PlanComment c WHERE c.planId = :planId ORDER BY c.createdAt ASC, c.id ASC")
    List<PlanComment> findCommentsByPlan(@Param("planId") Long planId);

    long countByPlanId(Long planId);

    @Modifying
    @Query(value = "INSERT INTO plan_usages (plan_id, user_id, used_count, last_used_on) "
            + "VALUES (:planId, :userId, 1, :usedOn) "
            + "ON DUPLICATE KEY UPDATE used_count = used_count + 1, "
            + "last_used_on = GREATEST(last_used_on, VALUES(last_used_on))", nativeQuery = true)
    void recordUsage(@Param("planId") Long planId, @Param("userId") Long userId, @Param("usedOn") LocalDate usedOn);

    @Query(value = "SELECT COUNT(*) FROM plan_usages WHERE plan_id = :planId", nativeQuery = true)
    long countTeachersWhoUsed(@Param("planId") Long planId);

    @Query(value = "SELECT last_used_on FROM plan_usages WHERE plan_id = :planId AND user_id = :userId", nativeQuery = true)
    List<java.sql.Date> lastUsedOn(@Param("planId") Long planId, @Param("userId") Long userId);
}
