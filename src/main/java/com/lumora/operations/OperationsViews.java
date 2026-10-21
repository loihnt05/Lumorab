package com.lumora.operations;

import com.lumora.booking.BookingViews;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OperationsViews {
  public static final String SIMULATED_REVENUE_LABEL = "SIMULATED_MOCK_REVENUE";
  public static final String CURRENCY = "VND";

  private OperationsViews() {}

  public record ManagerDashboard(
      UUID propertyId,
      String propertyName,
      String propertySlug,
      String propertyStatus,
      LocalDate date,
      long totalRooms,
      long activeRooms,
      long blockedRooms,
      long inactiveRooms,
      long occupiedRoomsToday,
      BigDecimal occupancyRatePercent,
      long bookingsToday,
      long checkInsToday,
      long checkOutsToday,
      long upcomingCheckIns,
      long upcomingCheckOuts,
      long checkedInCount,
      List<BookingViews.Summary> upcomingCheckInBookings,
      List<BookingViews.Summary> upcomingCheckOutBookings,
      List<BookingViews.Summary> recentBookings) {}

  public record BlockedRoomItem(
      UUID id,
      UUID propertyId,
      String propertyName,
      String roomNumber,
      String name,
      String status) {}

  public record AdminMonitorSummary(
      LocalDate date,
      UUID propertyId,
      LocalDate from,
      LocalDate to,
      long totalUsers,
      long totalRegularUsers,
      long totalManagers,
      long totalAdmins,
      long totalProperties,
      long activeProperties,
      long totalRooms,
      long activeRooms,
      long blockedRooms,
      long inactiveRooms,
      Map<String, Long> bookingsByStatus,
      long bookingsToday,
      long upcomingCheckIns,
      BigDecimal simulatedRevenue,
      long paidPaymentCount,
      String currency,
      String revenueLabel,
      List<BookingViews.Summary> latestBookings,
      List<BlockedRoomItem> blockedRoomList,
      List<BookingViews.Summary> upcomingCheckInList) {}

  public record DailyRevenuePoint(
      LocalDate date, long paidPaymentCount, BigDecimal simulatedRevenue) {}

  public record RevenueReport(
      LocalDate from,
      LocalDate to,
      UUID propertyId,
      BigDecimal totalSimulatedRevenue,
      long paidPaymentCount,
      String currency,
      String revenueLabel,
      List<DailyRevenuePoint> dailyTrend) {}

  public record DailyBookingPoint(LocalDate date, long bookingsCount) {}

  public record BookingsReport(
      LocalDate from,
      LocalDate to,
      UUID propertyId,
      long totalBookings,
      Map<String, Long> bookingsByStatus,
      List<DailyBookingPoint> dailyTrend) {}

  public record OperationalStatus(
      java.time.Instant checkedAt,
      LocalDate currentLocalDate,
      String timezone,
      String databaseStatus,
      int appliedMigrations,
      long overduePendingHolds,
      long activeRoomCodesToday,
      long pendingPaymentsCount,
      long sentEmailsCount,
      long failedEmailsCount) {}
}
