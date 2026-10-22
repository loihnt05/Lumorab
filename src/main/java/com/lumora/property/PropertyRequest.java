package com.lumora.property;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalTime;

public record PropertyRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
    @Size(max = 10000) String description,
    @Size(max = 500) String address,
    @Size(max = 120) String city,
    @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
    @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
    @NotNull LocalTime checkInTime,
    @NotNull LocalTime checkOutTime,
    @NotNull InventoryStatus status) {}
