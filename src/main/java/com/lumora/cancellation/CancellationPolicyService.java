package com.lumora.cancellation;

import com.lumora.booking.BookingStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CancellationPolicyService {
  private final CancellationProperties properties;

  public CancellationViews.PolicyQuote evaluate(
      UUID bookingId,
      String bookingCode,
      BookingStatus status,
      LocalDate checkInDate,
      BigDecimal totalAmount,
      BigDecimal paidAmount,
      String currency,
      Instant now) {
    Instant checkInAnchor = checkInDate.atStartOfDay(properties.zone()).toInstant();
    long hoursUntilCheckIn = Duration.between(now, checkInAnchor).toHours();
    BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
    BigDecimal normalizedPaid =
        (paidAmount == null ? BigDecimal.ZERO : paidAmount).setScale(2, RoundingMode.HALF_UP);
    BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    if (!status.canTransitionTo(BookingStatus.CANCELLED)) {
      return new CancellationViews.PolicyQuote(
          bookingId,
          bookingCode,
          status,
          false,
          "NOT_CANCELLABLE",
          0,
          normalizedTotal,
          normalizedPaid,
          zero,
          normalizedPaid,
          currency,
          hoursUntilCheckIn);
    }

    if (status == BookingStatus.PENDING_PAYMENT || normalizedPaid.compareTo(BigDecimal.ZERO) <= 0) {
      return new CancellationViews.PolicyQuote(
          bookingId,
          bookingCode,
          status,
          true,
          "UNPAID_HOLD",
          0,
          normalizedTotal,
          zero,
          zero,
          zero,
          currency,
          hoursUntilCheckIn);
    }

    String tier;
    int percentage;
    if (hoursUntilCheckIn >= properties.freeHoursBeforeCheckIn()) {
      tier = "FULL_REFUND";
      percentage = 100;
    } else if (hoursUntilCheckIn >= properties.partialHoursBeforeCheckIn()) {
      tier = "PARTIAL_REFUND";
      percentage = 50;
    } else {
      tier = "NO_REFUND";
      percentage = 0;
    }

    BigDecimal refundable =
        normalizedPaid
            .multiply(BigDecimal.valueOf(percentage))
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    BigDecimal fee = normalizedPaid.subtract(refundable).setScale(2, RoundingMode.HALF_UP);

    return new CancellationViews.PolicyQuote(
        bookingId,
        bookingCode,
        status,
        true,
        tier,
        percentage,
        normalizedTotal,
        normalizedPaid,
        refundable,
        fee,
        currency,
        hoursUntilCheckIn);
  }
}
