package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT AVG(c.rating) FROM Comment c WHERE c.resource.id = :resourceId")
    Double getAverageRatingByResourceId(String resourceId);

    Page<Comment> findByResourceId(String resourceId, Pageable pageable);
}
