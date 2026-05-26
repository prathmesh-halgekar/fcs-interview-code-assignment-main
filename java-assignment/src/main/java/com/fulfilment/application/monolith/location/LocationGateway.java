package com.fulfilment.application.monolith.location;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Reference data gateway for warehouse locations.
 * 
 * Provides access to predefined geographical locations used in the fulfillment system.
 * Currently backed by in-memory static data; can be extended to load from external sources.
 */
public class LocationGateway implements LocationResolver {

  private static final Logger LOGGER = Logger.getLogger(LocationGateway.class.getName());
  private static final List<Location> locations = new ArrayList<>();

  static {
    locations.add(new Location("ZWOLLE-001", 1, 40));
    locations.add(new Location("ZWOLLE-002", 2, 50));
    locations.add(new Location("AMSTERDAM-001", 5, 100));
    locations.add(new Location("AMSTERDAM-002", 3, 75));
    locations.add(new Location("TILBURG-001", 1, 40));
    locations.add(new Location("HELMOND-001", 1, 45));
    locations.add(new Location("EINDHOVEN-001", 2, 70));
    locations.add(new Location("VETSBY-001", 1, 90));
  }

  /**
   * Resolves a location by its unique identifier.
   * 
   * Validates that the identifier is not null or empty before searching.
   * Returns null if the identifier is invalid or location is not found.
   * 
   * @param identifier the location identifier (e.g., "AMSTERDAM-001")
   * @return the Location object if found; null if identifier is invalid or not found
   */
  @Override
  public Location resolveByIdentifier(String identifier) {
    if (identifier == null || identifier.isBlank()) {
      LOGGER.error("Location identifier cannot be null or empty");
      return null;
    }

    return locations.stream()
        .filter(location -> location.identification.equals(identifier))
        .findFirst()
        .orElse(null);
  }
}
