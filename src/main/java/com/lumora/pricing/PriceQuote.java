package com.lumora.pricing;

import java.math.BigDecimal;

public record PriceQuote(
    long numberOfNights,
    BigDecimal pricePerNight,
    BigDecimal subtotal,
    BigDecimal totalAmount,
    String currency) {}
