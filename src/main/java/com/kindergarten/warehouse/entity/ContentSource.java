package com.kindergarten.warehouse.entity;

/**
 * Who wrote a piece of plan content. Kept from day one so the AI assistant (phase 2)
 * can mark its suggestions and the UI can always tell them apart from teacher-written text.
 */
public enum ContentSource {
    TEACHER,
    AI_SUGGESTED
}
