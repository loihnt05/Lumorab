package com.lumora.identity.security;

import com.lumora.common.web.ApiError;
import com.lumora.common.web.RequestIdFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final JsonMapper mapper;

  public SecurityErrorHandler(JsonMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    if (exception instanceof DisabledException) {
      write(request, response, 403, "ACCOUNT_DISABLED", "This account is disabled.");
    } else if (exception instanceof AuthenticationServiceException) {
      write(
          request,
          response,
          503,
          "SERVICE_UNAVAILABLE",
          "Authentication is temporarily unavailable.");
    } else {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
      write(request, response, 401, "UNAUTHORIZED", "A valid Clerk session token is required.");
    }
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    write(
        request, response, 403, "FORBIDDEN", "You do not have permission to access this resource.");
  }

  private void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    mapper.writeValue(
        response.getOutputStream(),
        new ApiError(
            code, message, Map.of(), (String) request.getAttribute(RequestIdFilter.ATTRIBUTE)));
  }
}
