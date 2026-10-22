package com.lumora.inventory;

import static com.lumora.inventory.ContentRequests.*;
import static com.lumora.inventory.InventoryViews.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InventoryContentController {
  private final InventoryContentService content;

  @GetMapping("/api/amenities")
  public List<Amenity> amenities() {
    return content.amenities();
  }

  @SecurityRequirement(name = "clerkBearer")
  @PostMapping("/api/admin/amenities")
  @ResponseStatus(HttpStatus.CREATED)
  public Amenity createAmenity(@Valid @RequestBody AmenityRequest r) {
    return content.createAmenity(r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PutMapping("/api/admin/amenities/{id}")
  public Amenity updateAmenity(@PathVariable UUID id, @Valid @RequestBody AmenityRequest r) {
    return content.updateAmenity(id, r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @DeleteMapping("/api/admin/amenities/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAmenity(@PathVariable UUID id) {
    content.deleteAmenity(id);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PutMapping("/api/manager/properties/{propertyId}/room-types/{typeId}/amenities")
  public List<Amenity> replaceAmenities(
      @PathVariable UUID propertyId,
      @PathVariable UUID typeId,
      @Valid @RequestBody AmenitySelection r) {
    return content.replaceAmenities(propertyId, typeId, r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PostMapping("/api/manager/properties/{propertyId}/{kind:rooms|room-types}/{ownerId}/images")
  @ResponseStatus(HttpStatus.CREATED)
  public Image createImage(
      @PathVariable UUID propertyId,
      @PathVariable String kind,
      @PathVariable UUID ownerId,
      @Valid @RequestBody ImageRequest r) {
    return content.createImage(propertyId, ownerId, kind.equals("room-types"), r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PutMapping("/api/manager/properties/{propertyId}/{kind:rooms|room-types}/{ownerId}/images/{id}")
  public Image updateImage(
      @PathVariable UUID propertyId,
      @PathVariable String kind,
      @PathVariable UUID ownerId,
      @PathVariable UUID id,
      @Valid @RequestBody ImageRequest r) {
    return content.updateImage(propertyId, ownerId, kind.equals("room-types"), id, r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @DeleteMapping(
      "/api/manager/properties/{propertyId}/{kind:rooms|room-types}/{ownerId}/images/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteImage(
      @PathVariable UUID propertyId,
      @PathVariable String kind,
      @PathVariable UUID ownerId,
      @PathVariable UUID id) {
    content.deleteImage(propertyId, ownerId, kind.equals("room-types"), id);
  }
}
