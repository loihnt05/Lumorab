package com.lumora.inventory;

import com.lumora.common.persistence.AuditableEntity;
import com.lumora.property.InventoryStatus;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends AuditableEntity {
  @Column(nullable = false, updatable = false)
  private UUID propertyId;

  @Column(nullable = false)
  private UUID roomTypeId;

  @Column(nullable = false, length = 50)
  private String roomNumber;

  @Column(nullable = false)
  private String name;

  @Column(length = 50)
  private String floor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private InventoryStatus status;

  @Column(nullable = false)
  private boolean isBlocked;

  @Builder
  private Room(UUID propertyId) {
    this.propertyId = propertyId;
  }

  public void update(InventoryRequests.RoomRequest r) {
    roomTypeId = r.roomTypeId();
    roomNumber = r.roomNumber().trim();
    name = r.name().trim();
    floor = r.floor();
    status = r.status();
  }

  public void deactivate() {
    status = InventoryStatus.INACTIVE;
  }

  public void changeBlocked(boolean blocked) {
    isBlocked = blocked;
  }
}
