package com.lumora.inventory;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {
  List<RoomType> findByPropertyIdOrderByNameAscIdAsc(UUID propertyId);

  Optional<RoomType> findByIdAndPropertyId(UUID id, UUID propertyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RoomType t where t.id = :id and t.propertyId = :propertyId")
  Optional<RoomType> lock(UUID id, UUID propertyId);
}
