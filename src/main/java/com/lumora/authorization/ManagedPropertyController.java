package com.lumora.authorization;

import com.lumora.identity.security.CurrentUser;
import com.lumora.property.PropertySummary;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager/properties")
@SecurityRequirement(name = "clerkBearer")
public class ManagedPropertyController {
  private final ManagedPropertyService properties;

  public ManagedPropertyController(ManagedPropertyService properties) {
    this.properties = properties;
  }

  @GetMapping
  public List<PropertySummary> list(@AuthenticationPrincipal CurrentUser user) {
    return properties.list(user);
  }

  @GetMapping("/{propertyId}")
  public PropertySummary find(@PathVariable UUID propertyId) {
    return properties.find(propertyId);
  }
}
