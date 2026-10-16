package com.lumora.identity.security;

import com.lumora.identity.Role;
import java.security.Principal;
import java.util.UUID;

public record CurrentUser(UUID id, String clerkUserId, Role role) implements Principal {
  @Override
  public String getName() {
    return clerkUserId;
  }
}
