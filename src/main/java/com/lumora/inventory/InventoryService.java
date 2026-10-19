package com.lumora.inventory;

import static com.lumora.inventory.InventoryRequests.*;
import static com.lumora.inventory.InventoryViews.*;
import static com.lumora.property.PropertyService.notFound;

import com.lumora.property.*;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {
  private final PropertyRepository properties;
  private final RoomTypeRepository types;
  private final RoomRepository rooms;
  private final InventoryContentReader content;

  public List<RoomTypeView> publicTypes(UUID propertyId) {
    activeProperty(propertyId);
    return types.findByPropertyIdOrderByNameAscIdAsc(propertyId).stream()
        .filter(t -> t.getStatus() == InventoryStatus.ACTIVE)
        .map(this::view)
        .toList();
  }

  public RoomTypeView publicType(UUID propertyId, UUID id) {
    activeProperty(propertyId);
    var type = type(propertyId, id);
    if (type.getStatus() != InventoryStatus.ACTIVE) throw notFound();
    return view(type);
  }

  public List<RoomView> publicRooms(UUID propertyId) {
    activeProperty(propertyId);
    return rooms.findByPropertyIdOrderByRoomNumberAscIdAsc(propertyId).stream()
        .filter(r -> r.getStatus() == InventoryStatus.ACTIVE && !r.isBlocked())
        .filter(r -> type(propertyId, r.getRoomTypeId()).getStatus() == InventoryStatus.ACTIVE)
        .map(this::view)
        .toList();
  }

  public RoomView publicRoom(UUID propertyId, UUID id) {
    activeProperty(propertyId);
    var room = room(propertyId, id);
    if (room.getStatus() != InventoryStatus.ACTIVE
        || room.isBlocked()
        || type(propertyId, room.getRoomTypeId()).getStatus() != InventoryStatus.ACTIVE)
      throw notFound();
    return view(room);
  }

  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public List<RoomTypeView> managedTypes(UUID propertyId) {
    requireProperty(propertyId);
    return types.findByPropertyIdOrderByNameAscIdAsc(propertyId).stream().map(this::view).toList();
  }

  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public RoomTypeView managedType(UUID propertyId, UUID id) {
    return view(type(propertyId, id));
  }

  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public List<RoomView> managedRooms(UUID propertyId) {
    requireProperty(propertyId);
    return rooms.findByPropertyIdOrderByRoomNumberAscIdAsc(propertyId).stream()
        .map(this::view)
        .toList();
  }

  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public RoomView managedRoom(UUID propertyId, UUID id) {
    return view(room(propertyId, id));
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public RoomTypeView createType(UUID propertyId, @Valid RoomTypeRequest request) {
    requireProperty(propertyId);
    var type = RoomType.builder().propertyId(propertyId).build();
    type.update(request);
    return view(types.saveAndFlush(type));
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public RoomTypeView updateType(UUID propertyId, UUID id, @Valid RoomTypeRequest request) {
    var type = types.lock(id, propertyId).orElseThrow(PropertyService::notFound);
    type.update(request);
    return view(types.saveAndFlush(type));
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public void deactivateType(UUID propertyId, UUID id) {
    types.lock(id, propertyId).orElseThrow(PropertyService::notFound).deactivate();
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public RoomView createRoom(UUID propertyId, @Valid RoomRequest request) {
    activeTypeForRoom(propertyId, request.roomTypeId());
    var room = Room.builder().propertyId(propertyId).build();
    room.update(request);
    return view(rooms.saveAndFlush(room));
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public RoomView updateRoom(UUID propertyId, UUID id, @Valid RoomRequest request) {
    var room = lockedRoom(propertyId, id);
    if (request.status() == InventoryStatus.ACTIVE
        || !room.getRoomTypeId().equals(request.roomTypeId()))
      activeTypeForRoom(propertyId, request.roomTypeId());
    else type(propertyId, request.roomTypeId());
    room.update(request);
    return view(rooms.saveAndFlush(room));
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public void deactivateRoom(UUID propertyId, UUID id) {
    lockedRoom(propertyId, id).deactivate();
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public RoomView block(UUID id, @Valid BlockRequest request) {
    var room = rooms.lock(id).orElseThrow(PropertyService::notFound);
    room.changeBlocked(request.isBlocked());
    return view(rooms.saveAndFlush(room));
  }

  private void requireProperty(UUID id) {
    if (!properties.existsById(id)) throw notFound();
  }

  private void activeProperty(UUID id) {
    if (properties.findById(id).filter(p -> "ACTIVE".equals(p.getStatus())).isEmpty())
      throw notFound();
  }

  private RoomType type(UUID propertyId, UUID id) {
    return types.findByIdAndPropertyId(id, propertyId).orElseThrow(PropertyService::notFound);
  }

  private Room room(UUID propertyId, UUID id) {
    return rooms.findByIdAndPropertyId(id, propertyId).orElseThrow(PropertyService::notFound);
  }

  private Room lockedRoom(UUID propertyId, UUID id) {
    var room = rooms.lock(id).orElseThrow(PropertyService::notFound);
    if (!room.getPropertyId().equals(propertyId)) throw notFound();
    return room;
  }

  private void activeTypeForRoom(UUID propertyId, UUID id) {
    if (type(propertyId, id).getStatus() != InventoryStatus.ACTIVE)
      throw new ResponseStatusException(HttpStatus.CONFLICT);
  }

  private RoomTypeView view(RoomType t) {
    return RoomTypeView.from(t, content.typeImages(t.getId()), content.amenities(t.getId()));
  }

  private RoomView view(Room r) {
    return RoomView.from(
        r, view(type(r.getPropertyId(), r.getRoomTypeId())), content.roomImages(r.getId()));
  }
}
