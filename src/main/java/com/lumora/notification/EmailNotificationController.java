package com.lumora.notification;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "clerkBearer")
public class EmailNotificationController {
  private final EmailNotificationService notifications;

  @GetMapping("/api/admin/email-notifications")
  public ResponseEntity<EmailNotificationViews.Page> list(
      @Valid @ParameterObject @ModelAttribute EmailNotificationViews.Filter filter) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(notifications.list(filter));
  }
}
