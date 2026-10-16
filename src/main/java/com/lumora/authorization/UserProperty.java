package com.lumora.authorization;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_properties")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProperty {
  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "property_id", nullable = false)
  private UUID propertyId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Builder
  private UserProperty(UUID id, UUID userId, UUID propertyId, Instant createdAt) {
    this.id = id == null ? UUID.randomUUID() : id;
    this.userId = userId;
    this.propertyId = propertyId;
    this.createdAt = createdAt == null ? Instant.now() : createdAt;
  }
}
