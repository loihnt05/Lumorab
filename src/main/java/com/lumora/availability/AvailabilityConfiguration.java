package com.lumora.availability;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AvailabilityConfiguration {
  @Bean
  Clock availabilityClock() {
    return Clock.systemUTC();
  }
}
