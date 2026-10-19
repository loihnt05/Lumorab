package com.lumora.catalog;

import com.lumora.availability.AvailabilityRequest;
import com.lumora.booking.BookingStatus;
import com.lumora.inventory.InventoryViews.Amenity;
import com.lumora.inventory.InventoryViews.Image;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PublicCatalogRepository {
  private final NamedParameterJdbcTemplate jdbc;

  private static final String VISIBLE_ROOMS =
      """
      SELECT r.id, r.property_id, r.room_number, r.name, r.floor,
             t.id AS type_id, t.name AS type_name, t.description, t.base_price,
             t.max_guests, t.bed_type, t.bed_count, t.size_m2
      FROM rooms r
      JOIN properties p ON p.id=r.property_id
      JOIN room_types t ON t.id=r.room_type_id AND t.property_id=r.property_id
      WHERE p.status='ACTIVE' AND t.status='ACTIVE' AND r.status='ACTIVE'
        AND r.is_blocked=false
      """;

  public List<PublicViews.Room> rooms(UUID propertyId) {
    return query(
        " AND r.property_id=:propertyId", new MapSqlParameterSource("propertyId", propertyId));
  }

  public Optional<PublicViews.Room> room(UUID roomId) {
    return query(" AND r.id=:roomId", new MapSqlParameterSource("roomId", roomId)).stream()
        .findFirst();
  }

  public List<PublicViews.Room> available(AvailabilityRequest request, Instant now) {
    return query(
        """
        AND r.property_id=:propertyId AND t.max_guests>=:guests
        AND NOT EXISTS (
          SELECT 1 FROM booking_rooms br JOIN bookings b ON b.id=br.booking_id
          WHERE br.room_id=r.id
            AND b.check_in_date < :checkOut AND b.check_out_date > :checkIn
            AND (b.status IN (:occupiedStatuses)
                 OR (b.status=:pendingStatus AND b.expires_at > :now))
        )
        """,
        new MapSqlParameterSource()
            .addValue("propertyId", request.propertyId())
            .addValue("guests", request.guests())
            .addValue("checkIn", request.checkIn())
            .addValue("checkOut", request.checkOut())
            .addValue("occupiedStatuses", BookingStatus.occupiedStatuses())
            .addValue("pendingStatus", BookingStatus.PENDING_PAYMENT.name())
            .addValue("now", Timestamp.from(now)));
  }

  private List<PublicViews.Room> query(String filter, MapSqlParameterSource parameters) {
    var rooms =
        jdbc.query(
            VISIBLE_ROOMS + filter + " ORDER BY r.room_number,r.id",
            parameters,
            (rs, row) ->
                new PublicViews.Room(
                    rs.getObject("id", UUID.class),
                    rs.getObject("property_id", UUID.class),
                    rs.getString("room_number"),
                    rs.getString("name"),
                    rs.getString("floor"),
                    new PublicViews.RoomType(
                        rs.getObject("type_id", UUID.class),
                        rs.getString("type_name"),
                        rs.getString("description"),
                        rs.getBigDecimal("base_price"),
                        rs.getInt("max_guests"),
                        rs.getString("bed_type"),
                        rs.getInt("bed_count"),
                        rs.getBigDecimal("size_m2"),
                        List.of(),
                        List.of()),
                    List.of()));
    if (rooms.isEmpty()) return rooms;
    var roomIds = rooms.stream().map(PublicViews.Room::id).toList();
    var typeIds = rooms.stream().map(r -> r.roomType().id()).distinct().toList();
    var roomImages = images("room_id", roomIds);
    var typeImages = images("room_type_id", typeIds);
    Map<UUID, List<Amenity>> amenities = new HashMap<>();
    jdbc.query(
        """
        SELECT ra.room_type_id,a.id,a.name,a.icon FROM room_type_amenities ra
        JOIN amenities a ON a.id=ra.amenity_id
        WHERE ra.room_type_id IN (:ids) ORDER BY a.name,a.id
        """,
        new MapSqlParameterSource("ids", typeIds),
        rs -> {
          amenities
              .computeIfAbsent(rs.getObject(1, UUID.class), key -> new ArrayList<>())
              .add(new Amenity(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)));
        });
    return rooms.stream()
        .map(
            r ->
                r.withContent(
                    roomImages.getOrDefault(r.id(), List.of()),
                    typeImages.getOrDefault(r.roomType().id(), List.of()),
                    amenities.getOrDefault(r.roomType().id(), List.of())))
        .toList();
  }

  // Column names are private constants chosen by this repository, never request input.
  private Map<UUID, List<Image>> images(String column, List<UUID> ids) {
    Map<UUID, List<Image>> images = new HashMap<>();
    jdbc.query(
        "SELECT "
            + column
            + ",id,image_url,sort_order,is_cover FROM room_images WHERE "
            + column
            + " IN (:ids) ORDER BY sort_order,id",
        new MapSqlParameterSource("ids", ids),
        rs -> {
          images
              .computeIfAbsent(rs.getObject(1, UUID.class), key -> new ArrayList<>())
              .add(
                  new Image(
                      rs.getObject(2, UUID.class),
                      rs.getString(3),
                      rs.getInt(4),
                      rs.getBoolean(5)));
        });
    return images;
  }
}
