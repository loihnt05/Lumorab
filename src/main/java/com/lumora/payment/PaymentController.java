package com.lumora.payment;

import com.lumora.identity.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
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
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID bookingId,
      @RequestParam(required = false) String provider,
      @RequestBody(required = false) PaymentViews.CreateRequest body) {
    String requestedProvider =
        provider != null && !provider.isBlank()
            ? provider
            : (body != null ? body.provider() : null);
    var detail = payments.create(user.id(), bookingId, requestedProvider);
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

  @GetMapping({"/api/payments/{paymentId}/qr", "/api/bookings/{bookingId}/payments/{paymentId}/qr"})
  public ResponseEntity<PaymentViews.PaymentQr> paymentQr(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID paymentId) {
    return ok(payments.paymentQr(user, paymentId));
  }

  @PostMapping("/api/webhooks/payments/{provider}")
  public ResponseEntity<PaymentViews.WebhookResult> webhook(
      @PathVariable String provider,
      @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
      @Valid @RequestBody PaymentViews.WebhookRequest request) {
    return ok(payments.handleWebhook(provider, signature, request));
  }

  @PostMapping("/api/admin/payments/{paymentId}/reconcile")
  public ResponseEntity<PaymentViews.ReconcileResult> reconcilePayment(
      @PathVariable UUID paymentId,
      @RequestBody(required = false) PaymentViews.ReconcileRequest request) {
    return ok(payments.reconcilePayment(paymentId, request));
  }

  @PostMapping("/api/admin/bookings/{bookingId}/reconcile-payment")
  public ResponseEntity<PaymentViews.ReconcileResult> reconcileBookingPayment(
      @PathVariable UUID bookingId,
      @RequestBody(required = false) PaymentViews.ReconcileRequest request) {
    return ok(payments.reconcileBookingPayment(bookingId, request));
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
