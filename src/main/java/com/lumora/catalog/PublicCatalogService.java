package com.lumora.catalog;

import com.lumora.property.PropertyService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCatalogService {
  private final PropertyService properties;
  private final PublicCatalogRepository catalog;

  public List<PublicViews.PropertySummary> properties() {
    return properties.publicList().stream().map(PublicViews.PropertySummary::from).toList();
  }

  public PublicViews.Property property(String slug) {
    return PublicViews.Property.from(properties.publicFind(slug));
  }

  public List<PublicViews.Room> rooms(UUID propertyId) {
    properties.publicFind(propertyId.toString());
    return catalog.rooms(propertyId);
  }

  public PublicViews.Room room(UUID roomId) {
    return catalog.room(roomId).orElseThrow(PropertyService::notFound);
  }
}
