package com.lumora.identity.security;

import com.lumora.identity.IdentitySnapshot;
import com.lumora.identity.UserStatus;
import com.lumora.identity.UserSyncService;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

@Component
public class LocalUserAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {
  private final UserSyncService users;

  public LocalUserAuthenticationConverter(UserSyncService users) {
    this.users = users;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    try {
      var user = users.synchronize(IdentitySnapshot.fromVerifiedJwt(jwt));
      if (user.getStatus() != UserStatus.ACTIVE)
        throw new DisabledException("Local account is disabled.");
      var principal = new CurrentUser(user.getId(), user.getClerkUserId(), user.getRole());
      return UsernamePasswordAuthenticationToken.authenticated(
          principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    } catch (DataAccessException | TransactionException exception) {
      throw new AuthenticationServiceException("Identity storage is unavailable.", exception);
    }
  }
}
