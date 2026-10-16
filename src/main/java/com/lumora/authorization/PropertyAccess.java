package com.lumora.authorization;

import com.lumora.identity.Role;
import com.lumora.identity.security.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("propertyAccess")
public class PropertyAccess {
  private final AssignmentRepository assignments;

  public PropertyAccess(AssignmentRepository assignments) {
    this.assignments = assignments;
  }

  public boolean canManage(Authentication authentication, UUID propertyId) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof CurrentUser user)) return false;
    return user.role() == Role.ADMIN
        || (user.role() == Role.MANAGER && managerHasProperty(user.id(), propertyId));
  }

  public boolean managerHasProperty(UUID userId, UUID propertyId) {
    return assignments.existsByUserIdAndPropertyId(userId, propertyId);
  }
}
