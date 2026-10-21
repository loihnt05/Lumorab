package com.lumora.accesscode;

import com.lumora.identity.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RoomCodeController {
  private final RoomCodeService codes;

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/admin/rooms/{roomId}/access-code")
  public ResponseEntity<RoomCodeService.Code> find(
      @PathVariable UUID roomId, @RequestParam(required = false) LocalDate date) {
    return noStore(codes.find(roomId, date));
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/manager/properties/{propertyId}/rooms/{roomId}/access-code")
  public ResponseEntity<RoomCodeService.Code> findForPropertyManager(
      Authentication authentication, @PathVariable UUID propertyId, @PathVariable UUID roomId) {
    return noStore(codes.findCurrentForManager(authentication, propertyId, roomId));
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/manager/rooms/{roomId}/access-code")
  public ResponseEntity<RoomCodeService.Code> findForManager(
      Authentication authentication, @PathVariable UUID roomId) {
    return noStore(codes.findCurrentForManagerByRoom(authentication, roomId));
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping({"/api/me/bookings/{bookingId}/access-code", "/api/bookings/{bookingId}/access-code"})
  public ResponseEntity<RoomCodeService.Code> findForBookingGuest(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID bookingId) {
    return noStore(codes.findCurrentForBookingGuest(user, bookingId));
  }

  @SecurityRequirement(name = "clerkBearer")
  @PostMapping("/api/admin/room-access-codes/generate")
  public RoomCodeBatch.Result generate() {
    return codes.generateToday();
  }

  private <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
