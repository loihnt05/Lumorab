package com.lumora.payment;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {
  public static final String PROVIDER = "MOCK";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public PaymentAttempt createAttempt(
      UUID paymentId, String paymentCode, UUID bookingId, BigDecimal amount, String currency) {
    return new PaymentAttempt(
        PROVIDER, "MOCK-ATTEMPT-" + paymentId.toString().replace("-", "").toUpperCase(Locale.ROOT));
  }
}
