package com.lumora.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentGateway {
  String provider();

  PaymentAttempt createAttempt(
      UUID paymentId, String paymentCode, UUID bookingId, BigDecimal amount, String currency);

  record PaymentAttempt(String provider, String externalReference) {}
}
