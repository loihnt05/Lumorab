package com.lumora.payment;

import com.lumora.booking.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentViews {
  private PaymentViews() {}

  public record Detail(
      UUID id,
      UUID bookingId,
      String bookingCode,
      BookingStatus bookingStatus,
      String paymentCode,
      String provider,
      BigDecimal amount,
      String currency,
      PaymentStatus status,
      String transactionId,
      Instant paidAt,
      Instant createdAt,
      Instant updatedAt) {}
}
