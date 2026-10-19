package com.lumora.identity;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "clerkBearer")
public class AdminUserController {
  private final UserQueryService users;

  public AdminUserController(UserQueryService users) {
    this.users = users;
  }

  @GetMapping
  public ResponseEntity<AdminUserViews.Page> list(
      @Valid @ParameterObject @ModelAttribute AdminUserViews.Filter filter) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.list(filter));
  }

  @GetMapping("/{userId}")
  public UserProfile find(@PathVariable UUID userId) {
    return users.find(userId);
  }

  @PatchMapping("/{userId}/role")
  public ResponseEntity<UserProfile> changeRole(
      @PathVariable UUID userId, @Valid @RequestBody AdminUserViews.RoleRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(users.changeRole(userId, request.role()));
  }

  @PatchMapping("/{userId}/status")
  public ResponseEntity<UserProfile> changeStatus(
      @PathVariable UUID userId, @Valid @RequestBody AdminUserViews.StatusRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(users.changeStatus(userId, request.status()));
  }
}
