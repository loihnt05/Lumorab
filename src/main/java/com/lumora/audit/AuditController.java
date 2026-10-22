package com.lumora.audit;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@SecurityRequirement(name = "clerkBearer")
public class AuditController {
  private final AuditService audits;

  @GetMapping
  public ResponseEntity<AuditViews.Page> list(
      @Valid @ParameterObject @ModelAttribute AuditViews.Filter filter) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audits.list(filter));
  }
}
