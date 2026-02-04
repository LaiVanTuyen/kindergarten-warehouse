package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Favorite.FavoriteId> {
    Optional<Favorite> findByUserIdAndResourceId(Long userId, String resourceId);

    boolean existsByUserIdAndResourceId(Long userId, String resourceId);

    @Query("SELECT f.resourceId FROM Favorite f WHERE f.userId = :userId AND f.resourceId IN :resourceIds")
    Set<String> findFavoritedResourceIdsByUserIdAndResourceIdIn(@Param("userId") Long userId, @Param("resourceIds") List<String> resourceIds);
}
