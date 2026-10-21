package com.lumora.cancellation;

import com.lumora.identity.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "clerkBearer")
public class CancellationController {
  private final CancellationService cancellations;

  @GetMapping({
    "/api/bookings/{bookingId}/cancellation-policy",
    "/api/me/bookings/{bookingId}/cancellation-policy"
  })
  public ResponseEntity<CancellationViews.PolicyQuote> previewPolicy(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId) {
    return ok(cancellations.previewPolicy(user, bookingId));
  }

  @PostMapping({
    "/api/bookings/{bookingId}/cancel",
    "/api/me/bookings/{bookingId}/cancel",
    "/api/manager/bookings/{bookingId}/cancel",
    "/api/admin/bookings/{bookingId}/cancel"
  })
  public ResponseEntity<CancellationViews.CancelResult> cancel(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID bookingId,
      @Valid @RequestBody(required = false) CancellationViews.CancelRequest request) {
    return ok(cancellations.cancel(user, bookingId, request));
  }

  @GetMapping({"/api/bookings/{bookingId}/refund", "/api/me/bookings/{bookingId}/refund"})
  public ResponseEntity<CancellationViews.RefundDetail> getRefund(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId) {
    return ok(cancellations.getRefund(user, bookingId));
  }

  private <T> ResponseEntity<T> ok(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
