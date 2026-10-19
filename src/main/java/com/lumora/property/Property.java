package com.lumora.property;

import com.lumora.common.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalTime;
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

  @Column(columnDefinition = "text")
  private String description;

  @Column(length = 500)
  private String address;

  @Column(length = 120)
  private String city;

  @Column(precision = 9, scale = 6)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 6)
  private BigDecimal longitude;

  @Column(nullable = false)
  private LocalTime checkInTime = LocalTime.of(14, 0);

  @Column(nullable = false)
  private LocalTime checkOutTime = LocalTime.NOON;

  public void update(PropertyRequest request) {
    name = request.name().trim();
    slug = request.slug();
    description = request.description();
    address = request.address();
    city = request.city();
    latitude = request.latitude();
    longitude = request.longitude();
    checkInTime = request.checkInTime();
    checkOutTime = request.checkOutTime();
    status = request.status().name();
  }

  public void deactivate() {
    status = "INACTIVE";
  }

  @Builder
  private Property(String name, String slug, String status) {
    this.name = name;
    this.slug = slug;
    this.status = status == null ? "ACTIVE" : status;
  }
}
