package com.lumora.availability;

import com.lumora.catalog.PublicCatalogRepository;
import com.lumora.pricing.PricingService;
import com.lumora.property.PropertyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {
  private final PublicCatalogRepository catalog;
  private final PropertyService properties;
  private final PricingService pricing;
  private final Clock availabilityClock;

  public AvailabilityResponse search(@NotNull @Valid AvailabilityRequest request) {
    properties.publicFind(request.propertyId().toString());
    var now = availabilityClock.instant();
    var results =
        catalog.available(request, now).stream()
            .map(
                room ->
                    new AvailabilityResponse.Result(
                        room,
                        pricing.quote(
                            room.roomType().basePrice(), request.checkIn(), request.checkOut())))
            .toList();
    return new AvailabilityResponse(
        request.propertyId(),
        request.checkIn(),
        request.checkOut(),
        request.guests(),
        now,
        results);
  }
}
