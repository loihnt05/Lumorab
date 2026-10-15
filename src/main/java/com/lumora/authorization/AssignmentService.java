package com.lumora.authorization;

import com.lumora.identity.*;
import com.lumora.property.PropertyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AssignmentService {
  private final AssignmentRepository assignments;
  private final UserRepository users;
  private final PropertyRepository properties;

  public AssignmentService(
      AssignmentRepository assignments, UserRepository users, PropertyRepository properties) {
    this.assignments = assignments;
    this.users = users;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public List<UUID> list(UUID userId) {
    requireUser(userId);
    return assignments.findPropertyIds(userId);
  }

  @Transactional
  public void assign(UUID userId, UUID propertyId) {
    LocalUser user = requireUser(userId);
    if (user.getRole() != Role.MANAGER || user.getStatus() != UserStatus.ACTIVE) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Assignments require an active manager.");
    }
    if (!properties.existsById(propertyId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    assignments.assign(UUID.randomUUID(), userId, propertyId);
  }

  @Transactional
  public void revoke(UUID userId, UUID propertyId) {
    requireUser(userId);
    assignments.revoke(userId, propertyId);
  }

  private LocalUser requireUser(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
