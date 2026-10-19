package com.lumora.inventory;

import static com.lumora.inventory.InventoryRequests.*;
import static com.lumora.inventory.InventoryViews.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class InventoryController {
  private final InventoryService inventory;

  @GetMapping("/api/properties/{propertyId}/room-types")
  public List<RoomTypeView> publicTypes(@PathVariable UUID propertyId) {
    return inventory.publicTypes(propertyId);
  }

  @GetMapping("/api/properties/{propertyId}/room-types/{id}")
  public RoomTypeView publicType(@PathVariable UUID propertyId, @PathVariable UUID id) {
    return inventory.publicType(propertyId, id);
  }

  @GetMapping("/api/properties/{propertyId}/rooms")
  public List<RoomView> publicRooms(@PathVariable UUID propertyId) {
    return inventory.publicRooms(propertyId);
  }

  @GetMapping("/api/properties/{propertyId}/rooms/{id}")
  public RoomView publicRoom(@PathVariable UUID propertyId, @PathVariable UUID id) {
    return inventory.publicRoom(propertyId, id);
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/manager/properties/{propertyId}/room-types")
  public List<RoomTypeView> types(@PathVariable UUID propertyId) {
    return inventory.managedTypes(propertyId);
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/manager/properties/{propertyId}/room-types/{id}")
  public RoomTypeView type(@PathVariable UUID propertyId, @PathVariable UUID id) {
    return inventory.managedType(propertyId, id);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PostMapping("/api/manager/properties/{propertyId}/room-types")
  @ResponseStatus(HttpStatus.CREATED)
  public RoomTypeView createType(
      @PathVariable UUID propertyId, @Valid @RequestBody RoomTypeRequest r) {
    return inventory.createType(propertyId, r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PutMapping("/api/manager/properties/{propertyId}/room-types/{id}")
  public RoomTypeView updateType(
      @PathVariable UUID propertyId, @PathVariable UUID id, @Valid @RequestBody RoomTypeRequest r) {
    return inventory.updateType(propertyId, id, r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @DeleteMapping("/api/manager/properties/{propertyId}/room-types/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateType(@PathVariable UUID propertyId, @PathVariable UUID id) {
    inventory.deactivateType(propertyId, id);
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/manager/properties/{propertyId}/rooms")
  public List<RoomView> rooms(@PathVariable UUID propertyId) {
    return inventory.managedRooms(propertyId);
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/manager/properties/{propertyId}/rooms/{id}")
  public RoomView room(@PathVariable UUID propertyId, @PathVariable UUID id) {
    return inventory.managedRoom(propertyId, id);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PostMapping("/api/manager/properties/{propertyId}/rooms")
  @ResponseStatus(HttpStatus.CREATED)
  public RoomView createRoom(@PathVariable UUID propertyId, @Valid @RequestBody RoomRequest r) {
    return inventory.createRoom(propertyId, r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PutMapping("/api/manager/properties/{propertyId}/rooms/{id}")
  public RoomView updateRoom(
      @PathVariable UUID propertyId, @PathVariable UUID id, @Valid @RequestBody RoomRequest r) {
    return inventory.updateRoom(propertyId, id, r);
  }

  @SecurityRequirement(name = "clerkBearer")
  @DeleteMapping("/api/manager/properties/{propertyId}/rooms/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivateRoom(@PathVariable UUID propertyId, @PathVariable UUID id) {
    inventory.deactivateRoom(propertyId, id);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PatchMapping("/api/admin/rooms/{id}")
  public RoomView block(@PathVariable UUID id, @Valid @RequestBody BlockRequest r) {
    return inventory.block(id, r);
  }
}
