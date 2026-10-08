package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Runtime-tunable platform configuration managed by administrators. */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSetting extends BaseEntity {

    @Column(name = "setting_key", length = 120, nullable = false, unique = true)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "value_type", length = 16, nullable = false)
    private String valueType = "STRING";

    @Column(name = "description", length = 400)
    private String description;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;
}