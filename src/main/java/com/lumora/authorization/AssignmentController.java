package com.lumora.authorization;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users/{userId}/properties")
@SecurityRequirement(name = "clerkBearer")
public class AssignmentController {
  private final AssignmentService assignments;

  public AssignmentController(AssignmentService assignments) {
    this.assignments = assignments;
  }

  @GetMapping
  public List<UUID> list(@PathVariable UUID userId) {
    return assignments.list(userId);
  }

  @PutMapping("/{propertyId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assign(@PathVariable UUID userId, @PathVariable UUID propertyId) {
    assignments.assign(userId, propertyId);
  }

  @DeleteMapping("/{propertyId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID userId, @PathVariable UUID propertyId) {
    assignments.revoke(userId, propertyId);
  }
}
