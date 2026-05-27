package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import org.jboss.logging.Logger;

/**
 * Use case for creating new warehouses with composite validation.
 *
 * Enforces all business invariants before persisting:
 * 
 * Business unit code must be unique across active warehouses
 * Location must be a known, valid location identifier
 * Location must not have reached its maximum warehouse count
 * Warehouse capacity must not exceed the location's maximum capacity
 * Initial stock must be within [0, capacity] bounds
 */
@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

  private static final Logger LOGGER = Logger.getLogger(CreateWarehouseUseCase.class.getName());

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public CreateWarehouseUseCase(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  /**
   * Creates a new warehouse after validating all business rules.
   *
   * Validations are performed in the following order:
   * 
   * BUC uniqueness — 409 Conflict if already taken by an active warehouse
   * Location existence — 404 Not Found if location identifier is unknown
   * Warehouse count at location — 409 Conflict if maximum is reached
   * Capacity bounds — 400 Bad Request if capacity exceeds location maximum
   * Stock bounds — 400 Bad Request if stock exceeds capacity
   * 
   *
   * @param warehouse the warehouse to create; {@code businessUnitCode} and {@code location} must be set
   * @throws WebApplicationException 400 if warehouse is null or required fields are blank/invalid
   * @throws WebApplicationException 409 if BUC already exists or max warehouses reached at location
   * @throws WebApplicationException 404 if location is not found
   * @throws WebApplicationException 400 if capacity or stock is invalid
   */
  @Override
  public void create(Warehouse warehouse) {
    validateInputs(warehouse);
    LOGGER.debug("Validating warehouse creation for BUC: " + warehouse.businessUnitCode);

    validateBucUniqueness(warehouse.businessUnitCode);
    Location location = validateLocationExists(warehouse.location);
    validateWarehouseCountAtLocation(warehouse.location, location.maxNumberOfWarehouses);
    validateCapacity(warehouse.capacity, location.maxCapacity);
    validateStock(warehouse.stock, warehouse.capacity);

    warehouse.createdAt = LocalDateTime.now();
    warehouse.archivedAt = null;

    LOGGER.info(
        "All validations passed. Creating warehouse: "
            + warehouse.businessUnitCode
            + " at "
            + warehouse.location);
    warehouseStore.create(warehouse);
  }

  /**
   * Validates that all required input parameters are present and non-blank.
   *
   * @throws WebApplicationException 400 if warehouse is null or required fields are blank
   */
  private void validateInputs(Warehouse warehouse) {
    if (warehouse == null) {
      LOGGER.error("Warehouse object cannot be null");
      throw new WebApplicationException("Warehouse object cannot be null", 400);
    }

    if (warehouse.businessUnitCode == null || warehouse.businessUnitCode.isBlank()) {
      LOGGER.error("Business unit code cannot be null or blank");
      throw new WebApplicationException("Business unit code is required", 400);
    }

    if (warehouse.location == null || warehouse.location.isBlank()) {
      LOGGER.error("Location cannot be null or blank");
      throw new WebApplicationException("Location is required", 400);
    }

    if (warehouse.capacity == null || warehouse.capacity <= 0) {
      LOGGER.error("Capacity must be provided and positive");
      throw new WebApplicationException("Capacity must be a positive integer", 400);
    }

    if (warehouse.stock == null || warehouse.stock < 0) {
      LOGGER.error("Stock must be provided and non-negative");
      throw new WebApplicationException("Stock must be a non-negative integer", 400);
    }
  }

  /**
   * Ensures the given business unit code is not already in use by an active warehouse.
   *
   * @throws WebApplicationException 409 if BUC is already taken
   */
  private void validateBucUniqueness(String businessUnitCode) {
    if (warehouseStore.findByBusinessUnitCode(businessUnitCode) != null) {
      LOGGER.error("Business unit code already exists: " + businessUnitCode);
      throw new WebApplicationException(
          "Business unit code already exists: " + businessUnitCode, 409);
    }
  }

  /**
   * Resolves and returns the location by identifier, throwing 404 if it does not exist.
   *
   * @throws WebApplicationException 404 if location is unknown
   */
  private Location validateLocationExists(String locationId) {
    Location location = locationResolver.resolveByIdentifier(locationId);
    if (location == null) {
      LOGGER.error("Location not found: " + locationId);
      throw new WebApplicationException("Location not found: " + locationId, 404);
    }
    return location;
  }

  /**
   * Ensures the number of active warehouses at the given location is below the allowed maximum.
   *
   * @throws WebApplicationException 409 if maximum warehouse count is reached
   */
  private void validateWarehouseCountAtLocation(String locationId, int maxWarehouses) {
    long activeCount =
        warehouseStore.getAll().stream().filter(w -> locationId.equals(w.location)).count();
    if (activeCount >= maxWarehouses) {
      LOGGER.error(
          "Maximum warehouse count reached at location: "
              + locationId
              + " ("
              + activeCount
              + "/"
              + maxWarehouses
              + ")");
      throw new WebApplicationException(
          "Maximum number of warehouses already reached at location: " + locationId, 409);
    }
  }

  /**
   * Validates that capacity is positive and does not exceed the location's maximum.
   *
   * @throws WebApplicationException 400 if capacity is out of bounds
   */
  private void validateCapacity(int capacity, int maxCapacity) {
    if (capacity <= 0 || capacity > maxCapacity) {
      LOGGER.error("Invalid capacity: " + capacity + " (max allowed: " + maxCapacity + ")");
      throw new WebApplicationException(
          "Capacity must be between 1 and " + maxCapacity, 400);
    }
  }

  /**
   * Validates that stock is non-negative and does not exceed the warehouse capacity.
   *
   * @throws WebApplicationException 400 if stock is out of bounds
   */
  private void validateStock(int stock, int capacity) {
    if (stock < 0 || stock > capacity) {
      LOGGER.error("Invalid stock: " + stock + " (capacity: " + capacity + ")");
      throw new WebApplicationException(
          "Stock must be between 0 and capacity (" + capacity + ")", 400);
    }
  }
}
