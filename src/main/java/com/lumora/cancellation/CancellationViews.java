package com.lumora.cancellation;

import com.lumora.booking.BookingStatus;
import com.lumora.booking.BookingViews;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CancellationViews {
  private CancellationViews() {}

  public record CancelRequest(@Size(max = 500) String reason) {}

  public record PolicyQuote(
      UUID bookingId,
      String bookingCode,
      BookingStatus bookingStatus,
      boolean canCancel,
      String policyTier,
      int refundPercentage,
      BigDecimal totalAmount,
      BigDecimal paidAmount,
      BigDecimal refundableAmount,
      BigDecimal cancellationFeeAmount,
      String currency,
      long hoursUntilCheckIn) {}

  public record RefundDetail(
      UUID id,
      UUID bookingId,
      UUID paymentId,
      String refundCode,
      BigDecimal refundAmount,
      BigDecimal feeAmount,
      int refundPercentage,
      String currency,
      String policyTier,
      String status,
      String reason,
      Instant processedAt,
      Instant createdAt) {}

  public record CancelResult(
      BookingViews.Detail booking, PolicyQuote policy, RefundDetail refund) {}
}
