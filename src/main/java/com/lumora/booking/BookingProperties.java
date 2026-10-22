package com.lumora.booking;

import jakarta.validation.constraints.*;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.booking")
public record BookingProperties(
    @NotNull Duration holdDuration,
    @NotNull ZoneId zone,
    @Min(1) @Max(3650) int maxStayNights,
    @Min(1) @Max(1000) int expiryBatchSize) {
  @AssertTrue(message = "Hold duration must be between one second and one day.")
  public boolean isHoldDurationValid() {
    return holdDuration == null
        || (holdDuration.compareTo(Duration.ofSeconds(1)) >= 0
            && holdDuration.compareTo(Duration.ofDays(1)) <= 0);
  }
}
