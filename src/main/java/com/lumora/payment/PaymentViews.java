package com.lumora.payment;

import com.lumora.booking.BookingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentViews {
  private PaymentViews() {}

  public record CreateRequest(String provider) {}

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

  public record PaymentQr(
      UUID paymentId,
      UUID bookingId,
      String paymentCode,
      String provider,
      BigDecimal amount,
      String currency,
      PaymentStatus status,
      String qrPayload,
      String qrType) {}

  public record WebhookRequest(
      @NotBlank String eventId,
      @NotBlank String paymentCode,
      @NotBlank String status,
      @NotNull BigDecimal amount,
      @NotBlank String currency,
      @NotBlank String transactionId) {}

  public record WebhookResult(
      String eventId, String provider, boolean idempotentReplay, Detail payment) {}

  public record ReconcileRequest(String providerStatus, String transactionId) {}

  public record ReconcileResult(
      UUID bookingId, BookingStatus bookingStatus, Detail payment, String actionTaken) {}
}
