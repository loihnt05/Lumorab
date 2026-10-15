package com.lumora.property;

import com.lumora.common.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "properties")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Property extends AuditableEntity {
  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(nullable = false, length = 16)
  private String status;

  @Builder
  private Property(String name, String slug, String status) {
    this.name = name;
    this.slug = slug;
    this.status = status == null ? "ACTIVE" : status;
  }
}
