package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "resources", indexes = {
        @Index(name = "idx_resource_title", columnList = "title"),
        @Index(name = "idx_resource_status", columnList = "status"),
        @Index(name = "idx_resource_is_deleted", columnList = "is_deleted"),
        @Index(name = "idx_resource_topic", columnList = "topic_id"),
        @Index(name = "idx_resource_type", columnList = "resource_type")
})
@EqualsAndHashCode(of = "id", callSuper = false)
public class Resource extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "views_count")
    private Long viewsCount = 0L;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 20, columnDefinition = "VARCHAR(20)")
    private ResourceType resourceType; // FILE, YOUTUBE

    @Column(name = "file_type", length = 20)
    private String fileType; // VIDEO, DOCUMENT, PDF...

    @Column(name = "file_extension", length = 10)
    private String fileExtension;

    @Column(name = "highlights", columnDefinition = "json")
    @Convert(converter = com.kindergarten.warehouse.util.JsonStringListConverter.class)
    private java.util.List<String> highlights;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration", length = 20)
    private String duration; // e.g., "05:30", "12:00"

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, columnDefinition = "VARCHAR(20)")
    private ResourceStatus status = ResourceStatus.PENDING;

    @Builder.Default
    @Column(name = "download_count")
    private Long downloadCount = 0L;

    @Builder.Default
    @Column(name = "average_rating", columnDefinition = "DECIMAL(3, 2) DEFAULT 0.00")
    private Double averageRating = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    @ToString.Exclude
    private Topic topic;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Visibility visibility = Visibility.PUBLIC;

    @Builder.Default
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "resource_age_groups", joinColumns = @JoinColumn(name = "resource_id"), inverseJoinColumns = @JoinColumn(name = "age_group_id"))
    @ToString.Exclude
    @org.hibernate.annotations.BatchSize(size = 100)
    private java.util.Set<AgeGroup> ageGroups;
}
