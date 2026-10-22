package com.lumora.accesscode;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class RoomCodeGenerator {
  private final SecureRandom random = new SecureRandom();

  public String generate(int length) {
    if (length < 4 || length > 12) throw new IllegalArgumentException("Invalid code length.");
    var value = new StringBuilder(length);
    for (int i = 0; i < length; i++) value.append(random.nextInt(10));
    return value.toString();
  }
}
