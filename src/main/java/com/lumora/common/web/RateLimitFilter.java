package com.lumora.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
  public static final String CLIENT_HEADER = "X-Rate-Limit-Client";
  public static final String LIMIT_HEADER = "X-RateLimit-Limit";
  public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

  private final RateLimitProperties properties;
  private final JsonMapper json;
  private final Clock clock;
  private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

  private record WindowCounter(long minuteWindow, AtomicInteger count) {}

  @Autowired
  public RateLimitFilter(RateLimitProperties properties, JsonMapper json) {
    this(properties, json, Clock.systemUTC());
  }

  public RateLimitFilter(RateLimitProperties properties, JsonMapper json, Clock clock) {
    this.properties = properties;
    this.json = json;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public void reset() {
    counters.clear();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!Boolean.TRUE.equals(properties.enabled())) {
      return true;
    }
    String path = request.getRequestURI();
    if (path == null) return true;
    return !(path.startsWith("/api/public/")
        || path.startsWith("/api/properties")
        || path.startsWith("/api/amenities")
        || path.startsWith("/api/webhooks/payments/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    int limit = properties.requestsPerMinute();
    long currentMinute = clock.instant().getEpochSecond() / 60;
    if (counters.size() > 10_000) {
      counters.entrySet().removeIf(e -> e.getValue().minuteWindow() < currentMinute);
    }

    String clientKey = resolveClientKey(request);
    WindowCounter counter =
        counters.compute(
            clientKey,
            (k, existing) -> {
              if (existing == null || existing.minuteWindow() != currentMinute) {
                return new WindowCounter(currentMinute, new AtomicInteger(1));
              }
              existing.count().incrementAndGet();
              return existing;
            });

    int used = counter.count().get();
    int remaining = Math.max(0, limit - used);
    response.setHeader(LIMIT_HEADER, String.valueOf(limit));
    response.setHeader(REMAINING_HEADER, String.valueOf(remaining));

    if (used > limit) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setHeader("Retry-After", "60");
      response.setHeader("Cache-Control", "no-store");
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
      ApiError body =
          new ApiError(
              "TOO_MANY_REQUESTS",
              "Rate limit exceeded. Please try again later.",
              Map.of(),
              requestId);
      response.getWriter().write(json.writeValueAsString(body));
      return;
    }

    chain.doFilter(request, response);
  }

  private String resolveClientKey(HttpServletRequest request) {
    String explicitClient = request.getHeader(CLIENT_HEADER);
    if (explicitClient != null && !explicitClient.isBlank()) {
      return "client:" + explicitClient.trim();
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return "ip:" + (comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim());
    }
    String remoteAddr = request.getRemoteAddr();
    return "ip:" + (remoteAddr == null ? "unknown" : remoteAddr);
  }
}
