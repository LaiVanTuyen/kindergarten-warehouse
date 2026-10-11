package com.kindergarten.warehouse.entity;

/**
 * PRIVATE: only the owner. SHARED: any signed-in teacher/admin can open and copy it.
 * Sharing a collection never widens access to the resources inside it.
 */
public enum CollectionVisibility {
    PRIVATE,
    SHARED
}
