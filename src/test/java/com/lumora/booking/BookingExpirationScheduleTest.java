package com.lumora.booking;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BookingExpirationScheduleTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(BookingConfiguration.class, BookingExpirationSchedule.class)
          .withBean(BookingExpirationWorker.class, () -> mock(BookingExpirationWorker.class))
          .withPropertyValues(
              "app.booking.hold-duration=15m",
              "app.booking.zone=Asia/Ho_Chi_Minh",
              "app.booking.max-stay-nights=365",
              "app.booking.expiry-batch-size=100",
              "app.booking.expiry-interval=100ms");

  @Test
  void enabledWorkerRunsAndRetriesAfterFailure() {
    runner
        .withPropertyValues("app.booking.expiry-enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(BookingExpirationSchedule.class);
              var worker = context.getBean(BookingExpirationWorker.class);
              when(worker.runBatch())
                  .thenThrow(new IllegalStateException("test failure"))
                  .thenReturn(new BookingExpirationWorker.Result(0, 0, 0));
              verify(worker, timeout(3000).atLeast(3)).runBatch();
            });
  }

  @Test
  void canDisableScheduleWithoutRemovingWorker() {
    runner
        .withPropertyValues("app.booking.expiry-enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed().doesNotHaveBean(BookingExpirationSchedule.class);
              verifyNoInteractions(context.getBean(BookingExpirationWorker.class));
            });
  }

  @Test
  void invalidHoldConfigurationFailsStartup() {
    runner
        .withPropertyValues("app.booking.hold-duration=0s", "app.booking.expiry-enabled=false")
        .run(context -> assertThat(context).hasFailed());
  }
}
