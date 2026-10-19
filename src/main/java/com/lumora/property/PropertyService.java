package com.lumora.property;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertyService {
  private final PropertyRepository properties;

  public List<PropertyDetail> publicList() {
    return properties.findByStatusOrderByNameAsc("ACTIVE").stream()
        .map(PropertyDetail::from)
        .toList();
  }

  public PropertyDetail publicFind(String key) {
    var bySlug = properties.findBySlugAndStatus(key, "ACTIVE");
    if (bySlug.isPresent()) return PropertyDetail.from(bySlug.get());
    try {
      return PropertyDetail.from(
          properties
              .findById(UUID.fromString(key))
              .filter(p -> "ACTIVE".equals(p.getStatus()))
              .orElseThrow(PropertyService::notFound));
    } catch (IllegalArgumentException e) {
      throw notFound();
    }
  }

  @PreAuthorize("hasRole('ADMIN')")
  public List<PropertyDetail> adminList() {
    return properties.findAllByOrderByNameAsc().stream().map(PropertyDetail::from).toList();
  }

  @PreAuthorize("hasRole('ADMIN')")
  public PropertyDetail adminFind(UUID id) {
    return PropertyDetail.from(properties.findById(id).orElseThrow(PropertyService::notFound));
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public PropertyDetail create(@Valid PropertyRequest request) {
    var property = Property.builder().build();
    property.update(request);
    return PropertyDetail.from(properties.saveAndFlush(property));
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public PropertyDetail update(UUID id, @Valid PropertyRequest request) {
    var property = properties.findById(id).orElseThrow(PropertyService::notFound);
    property.update(request);
    return PropertyDetail.from(properties.saveAndFlush(property));
  }

  @Transactional
  @PreAuthorize("hasRole('ADMIN')")
  public void deactivate(UUID id) {
    properties.findById(id).orElseThrow(PropertyService::notFound).deactivate();
  }

  public static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }
}
