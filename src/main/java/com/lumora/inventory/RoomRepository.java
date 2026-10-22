package com.lumora.inventory;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RoomRepository extends JpaRepository<Room, UUID> {
  List<Room> findByPropertyIdOrderByRoomNumberAscIdAsc(UUID propertyId);

  Optional<Room> findByIdAndPropertyId(UUID id, UUID propertyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.id = :id")
  Optional<Room> lock(UUID id);
}
