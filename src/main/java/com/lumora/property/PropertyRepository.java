package com.lumora.property;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, UUID> {
  List<Property> findAllByOrderByNameAsc();

  List<Property> findByIdInOrderByNameAsc(Collection<UUID> ids);
}
