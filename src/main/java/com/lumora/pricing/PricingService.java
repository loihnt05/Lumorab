package com.lumora.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class PricingService {
  /** The rate must come from backend inventory, never from a client-supplied total. */
  public PriceQuote quote(BigDecimal pricePerNight, LocalDate checkIn, LocalDate checkOut) {
    if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
      throw new IllegalArgumentException("checkOut must be after checkIn.");
    }
    if (pricePerNight == null || pricePerNight.signum() <= 0) {
      throw new IllegalArgumentException("The inventory rate must be positive.");
    }
    long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
    BigDecimal subtotal = pricePerNight.multiply(BigDecimal.valueOf(nights));
    return new PriceQuote(nights, pricePerNight, subtotal, subtotal, "VND");
  }
}
