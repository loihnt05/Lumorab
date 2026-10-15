package com.lumora.identity;

import java.time.Instant;
import java.util.UUID;

public record UserProfile(
    UUID id,
    String clerkUserId,
    String email,
    String firstName,
    String lastName,
    String phone,
    Role role,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt) {
  public static UserProfile from(LocalUser user) {
    return new UserProfile(
        user.getId(),
        user.getClerkUserId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getPhone(),
        user.getRole(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
