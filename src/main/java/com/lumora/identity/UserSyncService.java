package com.lumora.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSyncService {
  private final UserIdentityWriter identities;
  private final UserRepository users;

  public UserSyncService(UserIdentityWriter identities, UserRepository users) {
    this.identities = identities;
    this.users = users;
  }

  @Transactional
  public LocalUser synchronize(IdentitySnapshot identity) {
    identities.upsert(identity);
    return users.findByClerkUserId(identity.clerkUserId()).orElseThrow();
  }
}
