package com.lumora.identity;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
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

  @GetMapping("/{userId}")
  public UserProfile find(@PathVariable UUID userId) {
    return users.find(userId);
  }
}
