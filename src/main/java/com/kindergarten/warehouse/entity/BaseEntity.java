package com.kindergarten.warehouse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.time.LocalDateTime;

@Getter
@Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String auditCreatedBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String auditUpdatedBy;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAuditCreatedBy() {
        return auditCreatedBy;
    }

    public void setAuditCreatedBy(String auditCreatedBy) {
        this.auditCreatedBy = auditCreatedBy;
    }

    public String getAuditUpdatedBy() {
        return auditUpdatedBy;
    }

    public void setAuditUpdatedBy(String auditUpdatedBy) {
        this.auditUpdatedBy = auditUpdatedBy;
    }
}
