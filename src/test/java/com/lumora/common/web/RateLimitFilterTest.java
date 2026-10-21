package com.lumora.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {
  @Test
  void enforcesPerMinuteQuotaAndResetsInNextWindow() throws Exception {
    var now = new AtomicReference<>(Instant.parse("2026-10-20T12:00:10Z"));
    Clock clock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return now.get();
          }
        };

    var filter =
        new RateLimitFilter(new RateLimitProperties(true, 2), JsonMapper.builder().build(), clock);

    for (int i = 1; i <= 2; i++) {
      var req = new MockHttpServletRequest("GET", "/api/public/properties");
      req.setAttribute(RequestIdFilter.ATTRIBUTE, "req-" + i);
      req.addHeader(RateLimitFilter.CLIENT_HEADER, "client-a");
      var res = new MockHttpServletResponse();
      filter.doFilter(req, res, new MockFilterChain());
      assertThat(res.getStatus()).isEqualTo(200);
      assertThat(res.getHeader(RateLimitFilter.LIMIT_HEADER)).isEqualTo("2");
      assertThat(res.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo(String.valueOf(2 - i));
    }

    // 3rd request in same minute is rejected with 429
    var blockedReq = new MockHttpServletRequest("GET", "/api/public/properties");
    blockedReq.setAttribute(RequestIdFilter.ATTRIBUTE, "req-3");
    blockedReq.addHeader(RateLimitFilter.CLIENT_HEADER, "client-a");
    var blockedRes = new MockHttpServletResponse();
    filter.doFilter(blockedReq, blockedRes, new MockFilterChain());
    assertThat(blockedRes.getStatus()).isEqualTo(429);
    assertThat(blockedRes.getHeader("Retry-After")).isEqualTo("60");
    assertThat(blockedRes.getContentAsString()).contains("TOO_MANY_REQUESTS", "req-3");

    // Different client is unaffected
    var otherClientReq = new MockHttpServletRequest("GET", "/api/public/properties");
    otherClientReq.addHeader(RateLimitFilter.CLIENT_HEADER, "client-b");
    var otherClientRes = new MockHttpServletResponse();
    filter.doFilter(otherClientReq, otherClientRes, new MockFilterChain());
    assertThat(otherClientRes.getStatus()).isEqualTo(200);

    // Next minute window resets quota for client-a
    now.set(Instant.parse("2026-10-20T12:01:05Z"));
    var nextWindowReq = new MockHttpServletRequest("GET", "/api/public/properties");
    nextWindowReq.addHeader(RateLimitFilter.CLIENT_HEADER, "client-a");
    var nextWindowRes = new MockHttpServletResponse();
    filter.doFilter(nextWindowReq, nextWindowRes, new MockFilterChain());
    assertThat(nextWindowRes.getStatus()).isEqualTo(200);
  }
}
