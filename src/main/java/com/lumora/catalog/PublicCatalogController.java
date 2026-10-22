package com.lumora.catalog;

import com.lumora.availability.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCatalogController {
  private final PublicCatalogService catalog;
  private final AvailabilityService availability;

  @GetMapping("/properties")
  public List<PublicViews.PropertySummary> properties() {
    return catalog.properties();
  }

  @GetMapping("/properties/{slug}")
  public PublicViews.Property property(@PathVariable String slug) {
    return catalog.property(slug);
  }

  @GetMapping("/properties/{propertyId}/rooms")
  public List<PublicViews.Room> rooms(@PathVariable UUID propertyId) {
    return catalog.rooms(propertyId);
  }

  @GetMapping("/rooms/{roomId}")
  public PublicViews.Room room(@PathVariable UUID roomId) {
    return catalog.room(roomId);
  }

  @GetMapping("/availability")
  public ResponseEntity<AvailabilityResponse> availability(
      @Valid @ParameterObject @ModelAttribute AvailabilityRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(availability.search(request));
  }
}
