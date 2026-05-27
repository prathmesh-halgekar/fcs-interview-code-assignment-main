package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import java.time.LocalDateTime;
import org.jboss.logging.Logger;

/**
 * Use case for replacing an active warehouse with a new one, reusing the business unit code.
 *
 * The replace operation is atomic: the old warehouse is archived and the new warehouse is
 * created within the same transaction. If either step fails, both are rolled back.
 *
 * Additional validations:
 * Old warehouse must exist and be active (not already archived)
 * New warehouse capacity must be sufficient to accommodate old warehouse stock
 * New warehouse stock must exactly match the old warehouse stock (no inventory loss)
 * New warehouse location must be valid if provided
 * 
 */
@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private static final Logger LOGGER = Logger.getLogger(ReplaceWarehouseUseCase.class.getName());

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  public ReplaceWarehouseUseCase(
      WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  /**
   * Replaces the currently active warehouse identified by {@code newWarehouse.businessUnitCode}
   * with the new warehouse data, atomically archiving the old and creating the new.
   *
   * <p>The caller (resource layer) must set {@code newWarehouse.businessUnitCode} to the business
   * unit code of the warehouse being replaced before invoking this method.
   *
   * @param newWarehouse the replacement warehouse data; {@code businessUnitCode} must identify
   *     the existing active warehouse to replace
   * @throws WebApplicationException 400 if newWarehouse is null or required fields are blank
   * @throws WebApplicationException 404 if no active warehouse exists for the given BUC
   * @throws WebApplicationException 404 if the new warehouse location is not found
   * @throws WebApplicationException 400 if new capacity cannot accommodate old stock
   * @throws WebApplicationException 400 if new stock does not match old warehouse stock
   */
  @Override
  public void replace(Warehouse newWarehouse) {
    validateReplaceInputs(newWarehouse);
    String buc = newWarehouse.businessUnitCode;
    LOGGER.debug("Starting warehouse replacement for BUC: " + buc);

    Warehouse oldWarehouse = warehouseStore.findByBusinessUnitCode(buc);
    if (oldWarehouse == null) {
      LOGGER.error("Warehouse to replace not found or already archived: " + buc);
      throw new WebApplicationException("Warehouse not found: " + buc, 404);
    }

    if (newWarehouse.location != null) {
      var location = locationResolver.resolveByIdentifier(newWarehouse.location);
      if (location == null) {
        LOGGER.error("New warehouse location not found: " + newWarehouse.location);
        throw new WebApplicationException(
            "Location not found: " + newWarehouse.location, 404);
      }
      validateCapacityWithinLocationBounds(newWarehouse.capacity, location.maxCapacity);
    }

    validateCapacityAccommodatesOldStock(newWarehouse.capacity, oldWarehouse.stock, buc);
    validateStockMatches(newWarehouse.stock, oldWarehouse.stock, buc);

    // Atomic swap: archive old, create new with the same BUC
    oldWarehouse.archivedAt = LocalDateTime.now();
    warehouseStore.update(oldWarehouse);
    LOGGER.debug("Old warehouse archived: " + buc + " at " + oldWarehouse.archivedAt);

    newWarehouse.createdAt = LocalDateTime.now();
    newWarehouse.archivedAt = null;
    warehouseStore.create(newWarehouse);
    LOGGER.info("Warehouse replaced: " + buc + " (old archived, new created)");
  }

  /**
   * Validates that all required input parameters for replace are present and non-blank.
   *
   * @throws WebApplicationException 400 if newWarehouse is null or required fields are blank
   */
  private void validateReplaceInputs(Warehouse newWarehouse) {
    if (newWarehouse == null) {
      LOGGER.error("Warehouse object cannot be null");
      throw new WebApplicationException("Warehouse object cannot be null", 400);
    }

    if (newWarehouse.businessUnitCode == null || newWarehouse.businessUnitCode.isBlank()) {
      LOGGER.error("Business unit code cannot be null or blank");
      throw new WebApplicationException("Business unit code is required", 400);
    }

    if (newWarehouse.location == null || newWarehouse.location.isBlank()) {
      LOGGER.error("Location cannot be null or blank");
      throw new WebApplicationException("Location is required", 400);
    }

    if (newWarehouse.capacity == null || newWarehouse.capacity <= 0) {
      LOGGER.error("Capacity must be provided and positive");
      throw new WebApplicationException("Capacity must be a positive integer", 400);
    }

    if (newWarehouse.stock == null || newWarehouse.stock < 0) {
      LOGGER.error("Stock must be provided and non-negative");
      throw new WebApplicationException("Stock must be a non-negative integer", 400);
    }
  }

  /**
   * Validates that the new warehouse capacity can hold the old warehouse's existing stock.
   *
   * @throws WebApplicationException 400 if new capacity is insufficient
   */
  private void validateCapacityAccommodatesOldStock(
      int newCapacity, int oldStock, String buc) {
    if (newCapacity < oldStock) {
      LOGGER.error(
          "New capacity ("
              + newCapacity
              + ") cannot accommodate old stock ("
              + oldStock
              + ") for BUC: "
              + buc);
      throw new WebApplicationException(
          "New warehouse capacity ("
              + newCapacity
              + ") must be at least the old warehouse stock ("
              + oldStock
              + ")",
          400);
    }
  }

  /**
   * Validates that the new warehouse stock exactly matches the old warehouse stock.
   *
   * @throws WebApplicationException 400 if stocks do not match
   */
  private void validateStockMatches(int newStock, int oldStock, String buc) {
    if (newStock != oldStock) {
      LOGGER.error(
          "Stock mismatch for BUC "
              + buc
              + ": new stock="
              + newStock
              + ", old stock="
              + oldStock);
      throw new WebApplicationException(
          "New warehouse stock ("
              + newStock
              + ") must match old warehouse stock ("
              + oldStock
              + ")",
          400);
    }
  }

  /**
   * Validates that the new warehouse capacity does not exceed the location's maximum capacity.
   *
   * @throws WebApplicationException 400 if capacity exceeds location maximum
   */
  private void validateCapacityWithinLocationBounds(int capacity, int maxCapacity) {
    if (capacity > maxCapacity) {
      LOGGER.error("Capacity " + capacity + " exceeds location maximum " + maxCapacity);
      throw new WebApplicationException(
          "Capacity ("
              + capacity
              + ") exceeds location maximum capacity ("
              + maxCapacity
              + ")",
          400);
    }
  }
}
