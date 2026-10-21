package com.lumora.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumora.booking.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CancellationPolicyTest {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final LocalDate CHECK_IN = LocalDate.of(2027, 5, 10);
  private static final Instant CHECK_IN_START = CHECK_IN.atStartOfDay(ZONE).toInstant();
  private static final BigDecimal AMOUNT = new BigDecimal("2000000.00");

  private final CancellationPolicyService service =
      new CancellationPolicyService(new CancellationProperties(48, 24, ZONE));

  @Test
  void confirmedPaidBookingSevenDaysBeforeCheckInGetsFullRefund() {
    var quote =
        service.evaluate(
            UUID.randomUUID(),
            "LM-TEST-7D",
            BookingStatus.CONFIRMED,
            CHECK_IN,
            AMOUNT,
            AMOUNT,
            "VND",
            CHECK_IN_START.minusSeconds(7 * 24 * 3600L));

    assertThat(quote.canCancel()).isTrue();
    assertThat(quote.policyTier()).isEqualTo("FULL_REFUND");
    assertThat(quote.refundPercentage()).isEqualTo(100);
    assertThat(quote.refundableAmount()).isEqualByComparingTo("2000000.00");
    assertThat(quote.cancellationFeeAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void confirmedPaidBookingAtExactFortyEightHoursGetsFullRefund() {
    var quote =
        service.evaluate(
            UUID.randomUUID(),
            "LM-TEST-48H",
            BookingStatus.CONFIRMED,
            CHECK_IN,
            AMOUNT,
            AMOUNT,
            "VND",
            CHECK_IN_START.minusSeconds(48 * 3600L));

    assertThat(quote.canCancel()).isTrue();
    assertThat(quote.policyTier()).isEqualTo("FULL_REFUND");
    assertThat(quote.refundPercentage()).isEqualTo(100);
    assertThat(quote.refundableAmount()).isEqualByComparingTo("2000000.00");
    assertThat(quote.cancellationFeeAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void confirmedPaidBookingBetweenTwentyFourAndFortyEightHoursGetsPartialRefund() {
    var quote =
        service.evaluate(
            UUID.randomUUID(),
            "LM-TEST-24H",
            BookingStatus.CONFIRMED,
            CHECK_IN,
            AMOUNT,
            AMOUNT,
            "VND",
            CHECK_IN_START.minusSeconds(24 * 3600L));

    assertThat(quote.canCancel()).isTrue();
    assertThat(quote.policyTier()).isEqualTo("PARTIAL_REFUND");
    assertThat(quote.refundPercentage()).isEqualTo(50);
    assertThat(quote.refundableAmount()).isEqualByComparingTo("1000000.00");
    assertThat(quote.cancellationFeeAmount()).isEqualByComparingTo("1000000.00");
  }

  @Test
  void confirmedPaidBookingOnCheckInDayGetsNoRefundButRemainsCancellable() {
    var quote =
        service.evaluate(
            UUID.randomUUID(),
            "LM-TEST-0H",
            BookingStatus.CONFIRMED,
            CHECK_IN,
            AMOUNT,
            AMOUNT,
            "VND",
            CHECK_IN_START);

    assertThat(quote.canCancel()).isTrue();
    assertThat(quote.policyTier()).isEqualTo("NO_REFUND");
    assertThat(quote.refundPercentage()).isEqualTo(0);
    assertThat(quote.refundableAmount()).isEqualByComparingTo("0.00");
    assertThat(quote.cancellationFeeAmount()).isEqualByComparingTo("2000000.00");
  }

  @Test
  void pendingPaymentHoldCanBeCancelledWithZeroRefundAndZeroFee() {
    var quote =
        service.evaluate(
            UUID.randomUUID(),
            "LM-TEST-HOLD",
            BookingStatus.PENDING_PAYMENT,
            CHECK_IN,
            AMOUNT,
            BigDecimal.ZERO,
            "VND",
            CHECK_IN_START.minusSeconds(72 * 3600L));

    assertThat(quote.canCancel()).isTrue();
    assertThat(quote.policyTier()).isEqualTo("UNPAID_HOLD");
    assertThat(quote.refundPercentage()).isEqualTo(0);
    assertThat(quote.refundableAmount()).isEqualByComparingTo("0.00");
    assertThat(quote.cancellationFeeAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void checkedInAndTerminalBookingsAreNotCancellable() {
    for (var status :
        new BookingStatus[] {
          BookingStatus.CHECKED_IN,
          BookingStatus.CHECKED_OUT,
          BookingStatus.COMPLETED,
          BookingStatus.CANCELLED,
          BookingStatus.EXPIRED
        }) {
      var quote =
          service.evaluate(
              UUID.randomUUID(),
              "LM-TEST-TERM",
              status,
              CHECK_IN,
              AMOUNT,
              AMOUNT,
              "VND",
              CHECK_IN_START);
      assertThat(quote.canCancel()).isFalse();
      assertThat(quote.policyTier()).isEqualTo("NOT_CANCELLABLE");
    }
  }
}
