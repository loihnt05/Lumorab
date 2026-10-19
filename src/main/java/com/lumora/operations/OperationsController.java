package com.lumora.operations;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "clerkBearer")
public class OperationsController {
  private final OperationsService operations;

  @GetMapping("/api/manager/properties/{propertyId}/dashboard")
  public ResponseEntity<OperationsViews.ManagerDashboard> managerDashboard(
      @PathVariable UUID propertyId, @RequestParam(required = false) LocalDate date) {
    return ok(operations.managerDashboard(propertyId, date));
  }

  @GetMapping("/api/admin/monitor/summary")
  public ResponseEntity<OperationsViews.AdminMonitorSummary> monitorSummary(
      @RequestParam(required = false) UUID propertyId,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return ok(operations.monitorSummary(propertyId, from, to));
  }

  @GetMapping("/api/admin/monitor/revenue")
  public ResponseEntity<OperationsViews.RevenueReport> monitorRevenue(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID propertyId) {
    return ok(operations.monitorRevenue(from, to, propertyId));
  }

  @GetMapping("/api/admin/monitor/bookings")
  public ResponseEntity<OperationsViews.BookingsReport> monitorBookings(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID propertyId) {
    return ok(operations.monitorBookings(from, to, propertyId));
  }

  private <T> ResponseEntity<T> ok(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
