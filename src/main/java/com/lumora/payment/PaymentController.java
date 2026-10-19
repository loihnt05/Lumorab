package com.lumora.payment;

import com.lumora.identity.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "clerkBearer")
public class PaymentController {
  private final PaymentService payments;

  @PostMapping("/api/bookings/{bookingId}/payments")
  public ResponseEntity<PaymentViews.Detail> create(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId) {
    var detail = payments.create(user.id(), bookingId);
    return ResponseEntity.created(
            URI.create("/api/bookings/" + bookingId + "/payments/" + detail.id()))
        .cacheControl(CacheControl.noStore())
        .body(detail);
  }

  @GetMapping("/api/bookings/{bookingId}/payments")
  public ResponseEntity<List<PaymentViews.Detail>> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId) {
    return ok(payments.listForBooking(user, bookingId));
  }

  @PostMapping("/api/mock-payments/{paymentId}/success")
  public ResponseEntity<PaymentViews.Detail> mockSuccess(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID paymentId) {
    return ok(payments.mockSuccess(user, paymentId));
  }

  @PostMapping("/api/mock-payments/{paymentId}/fail")
  public ResponseEntity<PaymentViews.Detail> mockFail(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID paymentId) {
    return ok(payments.mockFail(user, paymentId));
  }

  @PostMapping("/api/mock-payments/{paymentId}/cancel")
  public ResponseEntity<PaymentViews.Detail> mockCancel(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID paymentId) {
    return ok(payments.mockCancel(user, paymentId));
  }

  private <T> ResponseEntity<T> ok(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
