package com.lumora.identity.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.lumora.identity.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.*;
import org.springframework.security.oauth2.jwt.Jwt;

class LocalUserAuthenticationConverterTest {
  private final UserSyncService users = mock(UserSyncService.class);
  private final LocalUserAuthenticationConverter converter =
      new LocalUserAuthenticationConverter(users);
  private final Jwt jwt =
      new Jwt(
          "verified-test-token",
          Instant.now(),
          Instant.now().plusSeconds(60),
          Map.of("alg", "RS256"),
          Map.of("sub", "user_test", "role", "ADMIN", "scope", "admin"));

  @Test
  void grantsOnlyTheDatabaseRoleAndDoesNotKeepTheBearerToken() {
    var user =
        LocalUser.builder()
            .clerkUserId("user_test")
            .role(Role.USER)
            .status(UserStatus.ACTIVE)
            .build();
    when(users.synchronize(IdentitySnapshot.fromVerifiedJwt(jwt))).thenReturn(user);
    var authentication = converter.convert(jwt);
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_USER");
    assertThat(authentication.getName()).isEqualTo("user_test");
    assertThat(authentication.getCredentials()).isNull();
  }

  @Test
  void rejectsDisabledLocalAccounts() {
    var user =
        LocalUser.builder()
            .clerkUserId("user_test")
            .role(Role.USER)
            .status(UserStatus.DISABLED)
            .build();
    when(users.synchronize(IdentitySnapshot.fromVerifiedJwt(jwt))).thenReturn(user);
    assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(DisabledException.class);
  }

  @Test
  void failsClosedWhenDatabaseIsUnavailable() {
    when(users.synchronize(IdentitySnapshot.fromVerifiedJwt(jwt)))
        .thenThrow(new DataAccessResourceFailureException("private database details"));
    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(AuthenticationServiceException.class)
        .hasMessage("Identity storage is unavailable.");
  }
}
