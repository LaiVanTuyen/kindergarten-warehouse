package com.kindergarten.warehouse.entity;

/** "Duyệt chuyên môn" of a shared lesson plan. Editing approved content sends it back to NONE. */
public enum PlanReviewStatus {
    NONE,
    PENDING,
    APPROVED,
    CHANGES_REQUESTED
}
