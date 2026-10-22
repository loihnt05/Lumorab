package com.lumora.inventory;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryContentReader {
  private final JdbcTemplate jdbc;

  public List<InventoryViews.Image> roomImages(UUID id) {
    return images("room_id", id);
  }

  public List<InventoryViews.Image> typeImages(UUID id) {
    return images("room_type_id", id);
  }

  private List<InventoryViews.Image> images(String column, UUID id) {
    return jdbc.query(
        "SELECT id,image_url,sort_order,is_cover FROM room_images WHERE "
            + column
            + " = ? ORDER BY sort_order,id",
        (rs, n) ->
            new InventoryViews.Image(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getBoolean(4)),
        id);
  }

  public List<InventoryViews.Amenity> amenities(UUID typeId) {
    return jdbc.query(
        """
        SELECT a.id,a.name,a.icon FROM amenities a JOIN room_type_amenities ra ON ra.amenity_id=a.id
        WHERE ra.room_type_id=? ORDER BY a.name,a.id
        """,
        (rs, n) ->
            new InventoryViews.Amenity(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
        typeId);
  }
}
