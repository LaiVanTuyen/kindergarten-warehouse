package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Favorite.FavoriteId> {
    Optional<Favorite> findByUserIdAndResourceId(Long userId, String resourceId);

    boolean existsByUserIdAndResourceId(Long userId, String resourceId);
}
