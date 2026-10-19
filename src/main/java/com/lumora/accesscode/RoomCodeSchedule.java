package com.lumora.accesscode;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.room-codes.enabled", havingValue = "true", matchIfMissing = true)
public class RoomCodeSchedule {
  private static final Logger log = LoggerFactory.getLogger(RoomCodeSchedule.class);
  private final RoomCodeBatch batch;
  private final RoomCodeProperties properties;

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(
      cron = "${app.room-codes.cron:0 0 0 * * *}",
      zone = "${app.room-codes.zone:Asia/Ho_Chi_Minh}")
  public void run() {
    try {
      batch.generate(LocalDate.now(properties.zone()));
    } catch (RuntimeException exception) {
      // SQL exceptions may include bound secret values; log only the failure category.
      log.error(
          "Room code batch failed date={} category={}; retry the admin generation endpoint",
          LocalDate.now(properties.zone()),
          exception.getClass().getSimpleName());
    }
  }
}
