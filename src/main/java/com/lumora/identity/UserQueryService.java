package com.lumora.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserQueryService {
  private final UserRepository users;

  public UserQueryService(UserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public UserProfile find(UUID id) {
    return users
        .findById(id)
        .map(UserProfile::from)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
