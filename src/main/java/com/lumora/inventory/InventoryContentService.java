package com.lumora.inventory;

import static com.lumora.inventory.ContentRequests.*;
import static com.lumora.inventory.InventoryViews.*;
import static com.lumora.property.PropertyService.notFound;

import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryContentService {
  private final JdbcTemplate jdbc;
  private final InventoryContentReader content;

  public List<Amenity> amenities() {
    return jdbc.query(
        "SELECT id,name,icon FROM amenities ORDER BY name,id",
        (rs, n) -> new Amenity(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public Amenity createAmenity(@Valid AmenityRequest r) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO amenities(id,name,icon) VALUES (?,?,?)", id, r.name().trim(), r.icon());
    return new Amenity(id, r.name().trim(), r.icon());
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public Amenity updateAmenity(UUID id, @Valid AmenityRequest r) {
    if (jdbc.update("UPDATE amenities SET name=?,icon=? WHERE id=?", r.name().trim(), r.icon(), id)
        == 0) throw notFound();
    return new Amenity(id, r.name().trim(), r.icon());
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public void deleteAmenity(UUID id) {
    if (jdbc.update("DELETE FROM amenities WHERE id=?", id) == 0) throw notFound();
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public List<Amenity> replaceAmenities(UUID propertyId, UUID typeId, @Valid AmenitySelection r) {
    lockOwner(propertyId, typeId, true);
    for (UUID id : r.amenityIds())
      if (!Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT 1 FROM amenities WHERE id=?)", Boolean.class, id)))
        throw notFound();
    jdbc.update("DELETE FROM room_type_amenities WHERE room_type_id=?", typeId);
    for (UUID id : r.amenityIds())
      jdbc.update(
          "INSERT INTO room_type_amenities(room_type_id,amenity_id) VALUES (?,?)", typeId, id);
    return content.amenities(typeId);
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public Image createImage(UUID propertyId, UUID ownerId, boolean roomType, @Valid ImageRequest r) {
    lockOwner(propertyId, ownerId, roomType);
    String column = column(roomType);
    if (r.isCover()) clearCover(column, ownerId);
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO room_images(id,"
            + column
            + ",image_url,sort_order,is_cover) VALUES (?,?,?,?,?)",
        id,
        ownerId,
        r.imageUrl(),
        r.sortOrder(),
        r.isCover());
    return new Image(id, r.imageUrl(), r.sortOrder(), r.isCover());
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public Image updateImage(
      UUID propertyId, UUID ownerId, boolean roomType, UUID id, @Valid ImageRequest r) {
    lockOwner(propertyId, ownerId, roomType);
    String column = column(roomType);
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM room_images WHERE id=? AND " + column + "=?)",
            Boolean.class,
            id,
            ownerId))) throw notFound();
    if (r.isCover()) clearCover(column, ownerId);
    jdbc.update(
        "UPDATE room_images SET image_url=?,sort_order=?,is_cover=? WHERE id=? AND "
            + column
            + "=?",
        r.imageUrl(),
        r.sortOrder(),
        r.isCover(),
        id,
        ownerId);
    return new Image(id, r.imageUrl(), r.sortOrder(), r.isCover());
  }

  @Transactional
  @PreAuthorize("@propertyAccess.canManage(authentication, #propertyId)")
  public void deleteImage(UUID propertyId, UUID ownerId, boolean roomType, UUID id) {
    lockOwner(propertyId, ownerId, roomType);
    if (jdbc.update(
            "DELETE FROM room_images WHERE id=? AND " + column(roomType) + "=?", id, ownerId)
        == 0) throw notFound();
  }

  private String column(boolean roomType) {
    return roomType ? "room_type_id" : "room_id";
  }

  private void clearCover(String column, UUID ownerId) {
    jdbc.update(
        "UPDATE room_images SET is_cover=false WHERE " + column + "=? AND is_cover", ownerId);
  }

  private void lockOwner(UUID propertyId, UUID ownerId, boolean roomType) {
    String table = roomType ? "room_types" : "rooms";
    if (jdbc.queryForList(
            "SELECT id FROM " + table + " WHERE id=? AND property_id=? FOR UPDATE",
            UUID.class,
            ownerId,
            propertyId)
        .isEmpty()) throw notFound();
  }
}
