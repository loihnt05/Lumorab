package com.lumora.accesscode;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RoomCodeScheduleTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(RoomCodeConfiguration.class, RoomCodeSchedule.class)
          .withBean(RoomCodeBatch.class, () -> mock(RoomCodeBatch.class))
          .withPropertyValues(
              "app.room-codes.zone=Asia/Ho_Chi_Minh",
              "app.room-codes.length=6",
              "app.room-codes.cron=* * * * * *");

  @Test
  void schedulerExecutesUsingConfiguredZone() {
    runner
        .withPropertyValues("app.room-codes.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(RoomCodeSchedule.class);
              verify(context.getBean(RoomCodeBatch.class), timeout(3500).atLeastOnce())
                  .generate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
            });
  }

  @Test
  void schedulingCanBeDisabledWithoutRemovingManualBatch() {
    runner
        .withPropertyValues("app.room-codes.enabled=false")
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .doesNotHaveBean(RoomCodeSchedule.class)
                  .hasSingleBean(RoomCodeBatch.class);
              verifyNoInteractions(context.getBean(RoomCodeBatch.class));
            });
  }
}
