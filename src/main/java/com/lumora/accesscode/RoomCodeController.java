package com.lumora.accesscode;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RoomCodeController {
  private final RoomCodeService codes;

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/admin/rooms/{roomId}/access-code")
  public ResponseEntity<RoomCodeService.Code> find(
      @PathVariable UUID roomId, @RequestParam(required = false) LocalDate date) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(codes.find(roomId, date));
  }

  @SecurityRequirement(name = "clerkBearer")
  @PostMapping("/api/admin/room-access-codes/generate")
  public RoomCodeBatch.Result generate() {
    return codes.generateToday();
  }
}
