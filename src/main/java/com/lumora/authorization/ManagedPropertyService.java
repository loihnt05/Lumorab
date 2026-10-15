package com.lumora.authorization;

import com.lumora.identity.Role;
import com.lumora.identity.security.CurrentUser;
import com.lumora.property.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ManagedPropertyService {
  private final PropertyRepository properties;
  private final AssignmentRepository assignments;

  public ManagedPropertyService(PropertyRepository properties, AssignmentRepository assignments) {
    this.properties = properties;
    this.assignments = assignments;
  }

  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN') and #user.equals(authentication.principal)")
  public List<PropertySummary> list(CurrentUser user) {
    var result =
        user.role() == Role.ADMIN
            ? properties.findAllByOrderByNameAsc()
            : properties.findByIdInOrderByNameAsc(assignments.findPropertyIds(user.id()));
    return result.stream().map(PropertySummary::from).toList();
  }

  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public PropertySummary find(UUID propertyId) {
    return properties
        .findById(propertyId)
        .map(PropertySummary::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
