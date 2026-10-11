package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * "Kế hoạch hoạt động". Stored as structured sections + ordered steps so a later
 * AI assistant can draft or patch one part at a time (see V27).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "lesson_plans")
public class LessonPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @ToString.Exclude
    private User owner;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "age_group_id")
    @ToString.Exclude
    private AgeGroup ageGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    @ToString.Exclude
    private Topic topic;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "class_size")
    private Integer classSize;

    @Column(columnDefinition = "TEXT")
    private String objectives;

    @Column(columnDefinition = "TEXT")
    private String preparation;

    @Column(columnDefinition = "TEXT")
    private String adaptations;

    @Column(columnDefinition = "TEXT")
    private String reflection;

    @Column(name = "taught_on")
    private LocalDate taughtOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private LessonPlanStatus status = LessonPlanStatus.DRAFT;

    @Column(name = "is_template", nullable = false)
    @Builder.Default
    private Boolean template = false;

    @Column(name = "source_plan_id")
    private Long sourcePlanId;

    @Column(name = "collection_id")
    private Long collectionId;

    /*
     * Review fields are updatable = false: Hibernate writes every column of a dirty entity,
     * so an owner's save racing a reviewer's decision would otherwise overwrite it with the
     * stale value it loaded. They change only through LessonPlanRepository's conditional
     * UPDATEs (which also don't bump @Version).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)", updatable = false)
    @Builder.Default
    private PlanReviewStatus reviewStatus = PlanReviewStatus.NONE;

    @Column(name = "review_note", length = 1000, updatable = false)
    private String reviewNote;

    @Column(name = "reviewed_by", updatable = false)
    private Long reviewedBy;

    @Column(name = "reviewed_at", updatable = false)
    private LocalDateTime reviewedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @org.hibernate.annotations.BatchSize(size = 50)
    @ToString.Exclude
    @Builder.Default
    private List<LessonPlanStep> steps = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
