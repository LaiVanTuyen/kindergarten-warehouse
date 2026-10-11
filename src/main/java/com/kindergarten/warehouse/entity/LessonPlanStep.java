package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "lesson_plan_steps")
public class LessonPlanStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    @ToString.Exclude
    private LessonPlan plan;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private ContentSource source = ContentSource.TEACHER;

    /**
     * Resource references in display order. A hard-deleted resource cascades its row
     * away and can leave a gap (null element) — readers must skip nulls.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "lesson_plan_step_resources", joinColumns = @JoinColumn(name = "step_id"))
    @OrderColumn(name = "position")
    @Column(name = "resource_id", nullable = false, columnDefinition = "CHAR(36)")
    @org.hibernate.annotations.BatchSize(size = 50)
    @ToString.Exclude
    @Builder.Default
    private List<String> resourceIds = new ArrayList<>();
}
