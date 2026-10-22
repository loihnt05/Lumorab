package com.lumora.audit;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditServiceTest {
  @Test
  void redactsStandaloneSixDigitPinsWhilePreservingUuids() {
    UUID uuidWithSixDigits = UUID.fromString("123456ab-0000-4000-8000-123456abcdef");
    assertThat(AuditService.redactText("room=" + uuidWithSixDigits + " code=849201"))
        .isEqualTo("room=" + uuidWithSixDigits + " code=[REDACTED]");
    assertThat(AuditService.isSensitiveKey("codeValue")).isTrue();
    assertThat(AuditService.isSensitiveKey("access_code")).isTrue();
    assertThat(AuditService.isSensitiveKey("roomPin")).isTrue();
    assertThat(AuditService.isSensitiveKey("propertyId")).isFalse();
    assertThat(AuditService.isSensitiveKey("bookingCode")).isFalse();
  }
}
