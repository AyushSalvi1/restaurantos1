package com.lifeos.mapper;

import com.lifeos.dto.AuthDtos;
import com.lifeos.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static AuthDtos.UserResponse toResponse(User user) {
        return new AuthDtos.UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getAvatarUrl(),
                user.getTimezone(),
                user.getLocale(),
                user.getOccupation(),
                user.getEmploymentType(),
                user.isEmailVerified(),
                user.isOnboardingCompleted(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}