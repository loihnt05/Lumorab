package com.lumora.authorization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<UserProperty, UUID> {
  boolean existsByUserIdAndPropertyId(UUID userId, UUID propertyId);

  @Query("select a.propertyId from UserProperty a where a.userId = :userId order by a.propertyId")
  List<UUID> findPropertyIds(@Param("userId") UUID userId);

  @Modifying
  @Query(
      value =
          """
      INSERT INTO user_properties (id, user_id, property_id) VALUES (:id, :userId, :propertyId)
      ON CONFLICT (user_id, property_id) DO NOTHING
      """,
      nativeQuery = true)
  int assign(
      @Param("id") UUID id, @Param("userId") UUID userId, @Param("propertyId") UUID propertyId);

  @Modifying
  @Query("delete from UserProperty a where a.userId = :userId and a.propertyId = :propertyId")
  int revoke(@Param("userId") UUID userId, @Param("propertyId") UUID propertyId);
}
