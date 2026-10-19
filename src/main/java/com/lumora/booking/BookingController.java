package com.lumora.booking;

import com.lumora.identity.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "clerkBearer")
public class BookingController {
  private final BookingService bookings;

  @PostMapping("/api/bookings")
  public ResponseEntity<BookingViews.Detail> create(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateBookingRequest request) {
    var detail = bookings.create(user.id(), request);
    return ResponseEntity.created(URI.create("/api/me/bookings/" + detail.booking().id()))
        .cacheControl(CacheControl.noStore())
        .body(detail);
  }

  @GetMapping("/api/me/bookings")
  public ResponseEntity<BookingViews.Page> mine(
      @AuthenticationPrincipal CurrentUser user,
      @Valid @ParameterObject @ModelAttribute BookingFilter filter) {
    return ok(bookings.mine(user.id(), filter));
  }

  @GetMapping("/api/me/bookings/{id}")
  public ResponseEntity<BookingViews.Detail> mine(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
    return ok(bookings.mine(user.id(), id));
  }

  @GetMapping("/api/manager/properties/{propertyId}/bookings")
  public ResponseEntity<BookingViews.Page> managed(
      @PathVariable UUID propertyId, @Valid @ParameterObject @ModelAttribute BookingFilter filter) {
    return ok(bookings.managed(propertyId, filter));
  }

  @GetMapping("/api/manager/properties/{propertyId}/bookings/{id}")
  public ResponseEntity<BookingViews.Detail> managed(
      @PathVariable UUID propertyId, @PathVariable UUID id) {
    return ok(bookings.managed(propertyId, id));
  }

  @PostMapping("/api/manager/bookings/{id}/check-in")
  public ResponseEntity<BookingViews.Detail> checkIn(
      Authentication authentication, @PathVariable UUID id) {
    return ok(bookings.checkIn(authentication, id, null));
  }

  @PostMapping("/api/manager/bookings/{id}/check-out")
  public ResponseEntity<BookingViews.Detail> checkOut(
      Authentication authentication, @PathVariable UUID id) {
    return ok(bookings.checkOut(authentication, id, null));
  }

  @PostMapping("/api/manager/bookings/{id}/complete")
  public ResponseEntity<BookingViews.Detail> complete(
      Authentication authentication, @PathVariable UUID id) {
    return ok(bookings.complete(authentication, id, null));
  }

  @PostMapping("/api/manager/properties/{propertyId}/bookings/{id}/check-in")
  public ResponseEntity<BookingViews.Detail> checkInScoped(
      Authentication authentication, @PathVariable UUID propertyId, @PathVariable UUID id) {
    return ok(bookings.checkIn(authentication, id, propertyId));
  }

  @PostMapping("/api/manager/properties/{propertyId}/bookings/{id}/check-out")
  public ResponseEntity<BookingViews.Detail> checkOutScoped(
      Authentication authentication, @PathVariable UUID propertyId, @PathVariable UUID id) {
    return ok(bookings.checkOut(authentication, id, propertyId));
  }

  @PostMapping("/api/manager/properties/{propertyId}/bookings/{id}/complete")
  public ResponseEntity<BookingViews.Detail> completeScoped(
      Authentication authentication, @PathVariable UUID propertyId, @PathVariable UUID id) {
    return ok(bookings.complete(authentication, id, propertyId));
  }

  @GetMapping("/api/admin/bookings")
  public ResponseEntity<BookingViews.Page> all(
      @RequestParam(required = false) UUID propertyId,
      @Valid @ParameterObject @ModelAttribute BookingFilter filter) {
    return ok(bookings.all(propertyId, filter));
  }

  @GetMapping("/api/admin/bookings/{id}")
  public ResponseEntity<BookingViews.Detail> adminDetail(@PathVariable UUID id) {
    return ok(bookings.adminDetail(id));
  }

  private <T> ResponseEntity<T> ok(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
