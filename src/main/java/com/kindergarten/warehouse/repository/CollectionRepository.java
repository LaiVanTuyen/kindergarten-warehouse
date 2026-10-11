package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.CollectionVisibility;
import com.kindergarten.warehouse.entity.ResourceCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CollectionRepository extends JpaRepository<ResourceCollection, Long> {

    @EntityGraph(attributePaths = {"owner", "ageGroup", "topic"})
    @Query(value = "SELECT c FROM ResourceCollection c WHERE c.owner.id = :ownerId",
            countQuery = "SELECT COUNT(c) FROM ResourceCollection c WHERE c.owner.id = :ownerId")
    Page<ResourceCollection> findByOwner(@Param("ownerId") Long ownerId, Pageable pageable);

    /** Keyword is lower-cased and LIKE-escaped with "!" (see CollectionService); null = no filter. */
    @EntityGraph(attributePaths = {"owner", "ageGroup", "topic"})
    @Query(value = "SELECT c FROM ResourceCollection c WHERE c.visibility = :visibility "
            + "AND (:keyword IS NULL OR LOWER(c.title) LIKE :keyword ESCAPE '!' "
            + "OR LOWER(c.description) LIKE :keyword ESCAPE '!') "
            + "AND (:ageGroupId IS NULL OR c.ageGroup.id = :ageGroupId)",
            countQuery = "SELECT COUNT(c) FROM ResourceCollection c WHERE c.visibility = :visibility "
                    + "AND (:keyword IS NULL OR LOWER(c.title) LIKE :keyword ESCAPE '!' "
                    + "OR LOWER(c.description) LIKE :keyword ESCAPE '!') "
                    + "AND (:ageGroupId IS NULL OR c.ageGroup.id = :ageGroupId)")
    Page<ResourceCollection> searchByVisibility(@Param("visibility") CollectionVisibility visibility,
            @Param("keyword") String keyword, @Param("ageGroupId") Long ageGroupId, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "ageGroup", "topic"})
    @Query("SELECT c FROM ResourceCollection c WHERE c.id = :id")
    Optional<ResourceCollection> findWithDetailsById(@Param("id") Long id);

    @Query("SELECT c FROM ResourceCollection c WHERE c.owner.id = :ownerId ORDER BY c.updatedAt DESC")
    List<ResourceCollection> findAllByOwnerOrderByUpdatedAtDesc(@Param("ownerId") Long ownerId);

    long countByOwnerId(Long ownerId);

    /** [collectionId, itemCount] for list cards. */
    @Query("SELECT i.collection.id, COUNT(i) FROM CollectionItem i WHERE i.collection.id IN :ids GROUP BY i.collection.id")
    List<Object[]> countItemsByCollectionIds(@Param("ids") Collection<Long> ids);

    /** Collection ids (among the given) that already contain the resource — drives the "add to collection" dialog. */
    @Query("SELECT i.collection.id FROM CollectionItem i WHERE i.collection.id IN :ids AND i.resourceId = :resourceId")
    List<Long> findIdsContainingResource(@Param("ids") Collection<Long> ids, @Param("resourceId") String resourceId);
}
