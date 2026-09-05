package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.AgeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AgeGroupRepository extends JpaRepository<AgeGroup, Long> {
    boolean existsBySlug(String slug);

    @Query("SELECT r.id, ag FROM Resource r JOIN r.ageGroups ag WHERE r.id IN :resourceIds")
    List<Object[]> findAgeGroupsByResourceIds(@Param("resourceIds") Collection<String> resourceIds);
}
