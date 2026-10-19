package com.lumora.inventory;

import com.lumora.common.persistence.AuditableEntity;
import com.lumora.property.InventoryStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "room_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomType extends AuditableEntity {
  @Column(nullable = false, updatable = false)
  private UUID propertyId;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal basePrice;

  @Column(nullable = false)
  private int maxGuests;

  @Column(length = 100)
  private String bedType;

  @Column(nullable = false)
  private int bedCount;

  @Column(precision = 8, scale = 2)
  private BigDecimal sizeM2;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private InventoryStatus status;

  @Builder
  private RoomType(UUID propertyId) {
    this.propertyId = propertyId;
  }

  public void update(InventoryRequests.RoomTypeRequest r) {
    name = r.name().trim();
    description = r.description();
    basePrice = r.basePrice();
    maxGuests = r.maxGuests();
    bedType = r.bedType();
    bedCount = r.bedCount();
    sizeM2 = r.sizeM2();
    status = r.status();
  }

  public void deactivate() {
    status = InventoryStatus.INACTIVE;
  }
}
