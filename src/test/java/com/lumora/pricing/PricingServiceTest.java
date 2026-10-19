package com.lumora.pricing;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PricingServiceTest {
  private final PricingService pricing = new PricingService();

  @Test
  void threeNightsAtSixHundredThousand() {
    var quote =
        pricing.quote(
            new BigDecimal("600000.00"), LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2));
    assertThat(quote.numberOfNights()).isEqualTo(3);
    assertThat(quote.subtotal()).isEqualByComparingTo("1800000.00");
    assertThat(quote.totalAmount()).isEqualTo(quote.subtotal());
    assertThat(quote.currency()).isEqualTo("VND");
  }

  @Test
  void calendarNightsIncludeLeapDayAndKeepDecimalPrecision() {
    var quote =
        pricing.quote(
            new BigDecimal("123.45"), LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1));
    assertThat(quote.numberOfNights()).isEqualTo(2);
    assertThat(quote.totalAmount()).isEqualByComparingTo("246.90");
  }

  @Test
  void invalidDatesAndRatesAreRejected() {
    var day = LocalDate.of(2027, 1, 1);
    assertThatIllegalArgumentException().isThrownBy(() -> pricing.quote(BigDecimal.ONE, day, day));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pricing.quote(BigDecimal.ONE, day, day.minusDays(1)));
    assertThatIllegalArgumentException().isThrownBy(() -> pricing.quote(BigDecimal.ONE, null, day));
    assertThatIllegalArgumentException().isThrownBy(() -> pricing.quote(BigDecimal.ONE, day, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pricing.quote(BigDecimal.ZERO, day, day.plusDays(1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pricing.quote(BigDecimal.ONE.negate(), day, day.plusDays(1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pricing.quote(null, day, day.plusDays(1)));
  }
}
