package com.kindergarten.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Setter
@Getter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action; // CREATE, UPDATE, DELETE, LOGIN

    @Column(nullable = false)
    private String username; // Performer

    private String target; // Name of the affected object

    @Column(columnDefinition = "TEXT")
    private String detail; // Details

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public AuditLog() {
    }

    public AuditLog(Long id, String action, String username, String target, String detail, String ipAddress,
            String userAgent, LocalDateTime timestamp) {
        this.id = id;
        this.action = action;
        this.username = username;
        this.target = target;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.timestamp = timestamp;
    }

    public static AuditLogBuilder builder() {
        return new AuditLogBuilder();
    }

    public static class AuditLogBuilder {
        private Long id;
        private String action;
        private String username;
        private String target;
        private String detail;
        private String ipAddress;
        private String userAgent;
        private LocalDateTime timestamp;

        public AuditLogBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public AuditLogBuilder action(String action) {
            this.action = action;
            return this;
        }

        public AuditLogBuilder username(String username) {
            this.username = username;
            return this;
        }

        public AuditLogBuilder target(String target) {
            this.target = target;
            return this;
        }

        public AuditLogBuilder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public AuditLogBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public AuditLogBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public AuditLogBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AuditLog build() {
            return new AuditLog(id, action, username, target, detail, ipAddress, userAgent, timestamp);
        }
    }

//    public String getAction() {
//        return action;
//    }
//
//    public void setAction(String action) {
//        this.action = action;
//    }
//
//    public String getUsername() {
//        return username;
//    }
//
//    public void setUsername(String username) {
//        this.username = username;
//    }
//
//    public String getTarget() {
//        return target;
//    }
//
//    public void setTarget(String target) {
//        this.target = target;
//    }
//
//    public String getDetail() {
//        return detail;
//    }
//
//    public void setDetail(String detail) {
//        this.detail = detail;
//    }
//
//    public LocalDateTime getTimestamp() {
//        return timestamp;
//    }
//
//    public void setTimestamp(LocalDateTime timestamp) {
//        this.timestamp = timestamp;
//    }
}
