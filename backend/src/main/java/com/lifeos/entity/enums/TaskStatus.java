package com.lifeos.entity.enums;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isOpen() {
        return this == TODO || this == IN_PROGRESS;
    }
}