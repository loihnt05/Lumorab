package com.lumora.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumora.common.web.GlobalExceptionHandler;
import com.lumora.common.web.RequestIdFilter;
import com.lumora.config.SecurityConfig;
import com.lumora.identity.security.LocalUserAuthenticationConverter;
import com.lumora.identity.security.SecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = HealthController.class,
    properties = "app.cors.allowed-origins=http://localhost:3000")
@Import({
  SecurityConfig.class,
  GlobalExceptionHandler.class,
  RequestIdFilter.class,
  SecurityErrorHandler.class
})
class HealthControllerTest {
  @Autowired MockMvc mvc;
  @MockitoBean JdbcTemplate jdbc;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean LocalUserAuthenticationConverter converter;

  @Test
  void healthChecksDatabaseAndPropagatesRequestId() throws Exception {
    when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    mvc.perform(get("/api/health").header("X-Request-Id", "test-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.database").value("UP"))
        .andExpect(header().string("X-Request-Id", "test-123"));
  }

  @Test
  void databaseFailureReturnsSafeStructuredError() throws Exception {
    when(jdbc.queryForObject("SELECT 1", Integer.class))
        .thenThrow(new DataAccessResourceFailureException("secret connection info"));
    mvc.perform(get("/api/health"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("The service is temporarily unavailable."))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void businessEndpointsDenyAnonymousRequests() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void allowsConfiguredFrontendPreflight() throws Exception {
    mvc.perform(
            options("/api/health")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void rejectsUnknownOrigin() throws Exception {
    mvc.perform(
            options("/api/health")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }
}
