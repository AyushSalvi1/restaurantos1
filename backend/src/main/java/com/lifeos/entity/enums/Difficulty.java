package com.lifeos.entity.enums;

public enum Difficulty {
    EASY(1),
    MEDIUM(2),
    HARD(3);

    private final int multiplier;

    Difficulty(int multiplier) {
        this.multiplier = multiplier;
    }

    public int multiplier() {
        return multiplier;
    }
}