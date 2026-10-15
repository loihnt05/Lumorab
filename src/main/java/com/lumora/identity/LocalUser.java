package com.lumora.identity;

import com.lumora.common.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalUser extends AuditableEntity {
  @Column(name = "clerk_user_id", nullable = false, unique = true, length = 255)
  private String clerkUserId;

  @Column(length = 320)
  private String email;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(length = 32)
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private UserStatus status;

  @Column(name = "profile_synced_at")
  private Instant profileSyncedAt;

  @Builder
  private LocalUser(
      String clerkUserId,
      String email,
      String firstName,
      String lastName,
      String phone,
      Role role,
      UserStatus status,
      Instant profileSyncedAt) {
    this.clerkUserId = clerkUserId;
    this.email = email;
    this.firstName = firstName;
    this.lastName = lastName;
    this.phone = phone;
    this.role = role == null ? Role.USER : role;
    this.status = status == null ? UserStatus.ACTIVE : status;
    this.profileSyncedAt = profileSyncedAt;
  }
}
