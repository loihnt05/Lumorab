package com.lumora.cancellation;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CancellationProperties.class)
public class CancellationConfiguration {}

@ConfigurationProperties(prefix = "app.cancellation")
record CancellationProperties(
    Integer freeHoursBeforeCheckIn, Integer partialHoursBeforeCheckIn, ZoneId zone) {
  CancellationProperties {
    freeHoursBeforeCheckIn = freeHoursBeforeCheckIn == null ? 48 : freeHoursBeforeCheckIn;
    partialHoursBeforeCheckIn = partialHoursBeforeCheckIn == null ? 24 : partialHoursBeforeCheckIn;
    zone = zone == null ? ZoneId.of("Asia/Ho_Chi_Minh") : zone;
  }
}
