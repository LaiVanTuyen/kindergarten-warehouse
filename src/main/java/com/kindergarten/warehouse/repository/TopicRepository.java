package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {
    java.util.List<Topic> findByCategoryId(Long categoryId);
}
