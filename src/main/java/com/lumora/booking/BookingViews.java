package com.lumora.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BookingViews {
  private BookingViews() {}

  public record Summary(
      UUID id,
      String bookingCode,
      UUID propertyId,
      String propertyName,
      LocalDate checkIn,
      LocalDate checkOut,
      BookingStatus status,
      int guests,
      BigDecimal totalAmount,
      String currency,
      Instant expiresAt,
      Instant createdAt) {}

  public record RoomLine(
      UUID roomId,
      UUID roomTypeId,
      String roomNumber,
      String roomName,
      BigDecimal pricePerNight,
      int numberOfNights,
      BigDecimal roomTotal) {}

  public record Guest(String fullName, String phone, String email, boolean isPrimaryGuest) {}

  public record Detail(
      Summary booking,
      BigDecimal subtotal,
      BigDecimal discountAmount,
      String specialRequest,
      List<RoomLine> rooms,
      List<Guest> guests) {}

  public record Page(List<Summary> items, int page, int size, long totalElements) {}
}
