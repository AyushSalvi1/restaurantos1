package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Immutable audit trail of security-relevant actions. Written for administrative
 * and compliance review; entries are never exposed to non-admin users.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog extends CreatedEntity {

    @Column(name = "actor_user_id", length = 36)
    private String actorUserId;

    @Column(name = "action", length = 64, nullable = false)
    private String action;

    @Column(name = "entity_type", length = 48)
    private String entityType;

    @Column(name = "entity_id", length = 36)
    private String entityId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;
}