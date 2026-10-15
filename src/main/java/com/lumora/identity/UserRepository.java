package com.lumora.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<LocalUser, UUID> {
  Optional<LocalUser> findByClerkUserId(String clerkUserId);
}
