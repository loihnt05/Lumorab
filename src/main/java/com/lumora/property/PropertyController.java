package com.lumora.property;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PropertyController {
  private final PropertyService properties;

  @GetMapping("/api/properties")
  public List<PropertyDetail> list() {
    return properties.publicList();
  }

  @GetMapping("/api/properties/{key}")
  public PropertyDetail find(@PathVariable String key) {
    return properties.publicFind(key);
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/admin/properties")
  public List<PropertyDetail> adminList() {
    return properties.adminList();
  }

  @SecurityRequirement(name = "clerkBearer")
  @GetMapping("/api/admin/properties/{id}")
  public PropertyDetail adminFind(@PathVariable UUID id) {
    return properties.adminFind(id);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PostMapping("/api/admin/properties")
  @ResponseStatus(HttpStatus.CREATED)
  public PropertyDetail create(@Valid @RequestBody PropertyRequest request) {
    return properties.create(request);
  }

  @SecurityRequirement(name = "clerkBearer")
  @PutMapping("/api/admin/properties/{id}")
  public PropertyDetail update(@PathVariable UUID id, @Valid @RequestBody PropertyRequest request) {
    return properties.update(id, request);
  }

  @SecurityRequirement(name = "clerkBearer")
  @DeleteMapping("/api/admin/properties/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deactivate(@PathVariable UUID id) {
    properties.deactivate(id);
  }
}
