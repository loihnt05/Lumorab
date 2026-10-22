package com.lumora.accesscode;

import jakarta.validation.constraints.*;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.room-codes")
public record RoomCodeProperties(@NotNull ZoneId zone, @Min(4) @Max(12) int length) {}
