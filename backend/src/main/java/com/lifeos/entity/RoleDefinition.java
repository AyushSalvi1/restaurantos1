package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Authorisation roles are data, not enums, so new roles can be introduced without
 * a schema or code change. The application-level {@link com.lifeos.entity.enums.Role}
 * enum covers the built-in roles shipped as seed data.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class RoleDefinition extends CreatedEntity {

    @Column(name = "code", length = 32, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "description", length = 255)
    private String description;
}