package com.lumora.identity;

import com.lumora.identity.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "clerkBearer")
public class MeController {
  private final UserQueryService users;

  public MeController(UserQueryService users) {
    this.users = users;
  }

  @GetMapping("/api/me")
  public UserProfile me(@AuthenticationPrincipal CurrentUser user) {
    return users.find(user.id());
  }
}
